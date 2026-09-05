package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.shared.observability.BusinessMetrics;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisbursementRetrySweepServiceTest {

    @Mock
    private DisbursementRepository disbursementRepository;
    @Mock
    private DarajaB2CService darajaB2CService;
    @Mock
    private PlatformSettingsService platformSettingsService;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private FinancialAuditService financialAuditService;
    @Mock
    private BusinessMetrics metrics;

    private DisbursementRetrySweepService sweepService;

    private final UUID disbursementId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("5000.00");
    private static final String REGISTERED_PAYOUT_PHONE = "+254712345678";

    private Disbursement failedDisbursement;

    @BeforeEach
    void setUp() {
        lenient().when(platformSettingsService.getEffectiveSettings())
                .thenReturn(PlatformSettings.defaults("test"));
        sweepService = new DisbursementRetrySweepService(
                disbursementRepository, darajaB2CService, platformSettingsService,
                tenantRepository, financialAuditService, metrics);

        // A retry now re-checks that the row's recipient is still the
        // landlord's registered payout number, so these fixtures have to
        // supply a landlord whose number matches. That is the real
        // precondition for a legitimate retry, not test scaffolding — a row
        // pointing anywhere else is refused, which is what
        // DisbursementRetryGuardTest covers.
        Tenant landlord = org.mockito.Mockito.mock(Tenant.class);
        lenient().when(landlord.getPayoutPhoneNumber()).thenReturn(REGISTERED_PAYOUT_PHONE);
        lenient().when(tenantRepository.findById(any())).thenReturn(Optional.of(landlord));

        failedDisbursement = Disbursement.rehydrate(
                disbursementId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                amount, REGISTERED_PAYOUT_PHONE, "Test Landlord",
                "BusinessPayment", DisbursementStatus.FAILED,
                null, "CONV_FAIL", "OCID_FAIL",
                "Queue timeout", 1, false,
                Instant.now().minusSeconds(3600), Instant.now(), "KES", 0L
        );
    }

    @Nested
    class RetryOne {

        @Test
        void retriesFailedDisbursement() {
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(failedDisbursement));
            when(darajaB2CService.initiateB2C(
                    eq(amount), eq(REGISTERED_PAYOUT_PHONE), eq("Test Landlord"),
                    anyString(), eq("BusinessPayment")
            )).thenReturn("OCID_RETRY");
            when(disbursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sweepService.retryOne(disbursementId);

            assertThat(failedDisbursement.getStatus()).isEqualTo(DisbursementStatus.PENDING);
            assertThat(failedDisbursement.getMpesaOriginatorConversationId()).isEqualTo("OCID_RETRY");
            verify(disbursementRepository).save(failedDisbursement);
        }

        @Test
        void flagsForManualAttentionWhenRetriesExhausted() {
            Disbursement exhausted = Disbursement.rehydrate(
                    disbursementId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    amount, REGISTERED_PAYOUT_PHONE, "Test Landlord",
                    "BusinessPayment", DisbursementStatus.FAILED,
                    null, "CONV_FAIL", "OCID_FAIL",
                    "Queue timeout", 3, false,
                    Instant.now().minusSeconds(3600), Instant.now(), "KES", 0L
            );
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(exhausted));
            when(disbursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sweepService.retryOne(disbursementId);

            assertThat(exhausted.getStatus()).isEqualTo(DisbursementStatus.FAILED);
            assertThat(exhausted.isRequiresManualAttention()).isTrue();
            verify(disbursementRepository).save(exhausted);
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void skipsWhenNotFailed() {
            Disbursement pending = Disbursement.rehydrate(
                    disbursementId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    amount, REGISTERED_PAYOUT_PHONE, "Test Landlord",
                    "BusinessPayment", DisbursementStatus.PENDING,
                    null, "CONV", "OCID",
                    null, 0, false,
                    Instant.now(), Instant.now(), "KES", 0L
            );
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(pending));

            sweepService.retryOne(disbursementId);

            verify(disbursementRepository, never()).save(any());
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void skipsWhenNotFound() {
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.empty());

            sweepService.retryOne(disbursementId);

            verify(disbursementRepository, never()).save(any());
            verifyNoInteractions(darajaB2CService);
        }

        @Test
        void marksFailedAgainWhenB2CThrows() {
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(failedDisbursement));
            when(darajaB2CService.initiateB2C(
                    any(), anyString(), anyString(), anyString(), anyString()
            )).thenThrow(new RuntimeException("Daraja API error"));
            when(disbursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sweepService.retryOne(disbursementId);

            assertThat(failedDisbursement.getStatus()).isEqualTo(DisbursementStatus.FAILED);
            assertThat(failedDisbursement.getRetryCount()).isEqualTo(2);
            assertThat(failedDisbursement.getFailureReason()).contains("Daraja API error");
            verify(disbursementRepository).save(failedDisbursement);
        }

        @Test
        void respectsRetryCapOfThree() {
            Disbursement atCap = Disbursement.rehydrate(
                    disbursementId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    amount, REGISTERED_PAYOUT_PHONE, "Test Landlord",
                    "BusinessPayment", DisbursementStatus.FAILED,
                    null, "CONV_FAIL", "OCID_FAIL",
                    "Network error", 2, false,
                    Instant.now().minusSeconds(3600), Instant.now(), "KES", 0L
            );
            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(atCap));
            when(darajaB2CService.initiateB2C(
                    any(), anyString(), anyString(), anyString(), anyString()
            )).thenThrow(new RuntimeException("Still down"));
            when(disbursementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            sweepService.retryOne(disbursementId);

            assertThat(atCap.getRetryCount()).isEqualTo(3);
            assertThat(atCap.isRequiresManualAttention()).isFalse();
            assertThat(atCap.getStatus()).isEqualTo(DisbursementStatus.FAILED);

            when(disbursementRepository.findById(disbursementId))
                    .thenReturn(Optional.of(atCap));
            sweepService.retryOne(disbursementId);

            assertThat(atCap.isRequiresManualAttention()).isTrue();
            verify(darajaB2CService, times(1)).initiateB2C(any(), anyString(), anyString(), anyString(), anyString());
        }
    }
}
