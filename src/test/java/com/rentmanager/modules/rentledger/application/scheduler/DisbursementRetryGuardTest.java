package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retrying a payout is a fresh money movement and has to satisfy the same
 * control as raising one: the destination must be the landlord's registered
 * payout number.
 *
 * <p>Without this, the guards added to {@code B2CDisbursementService} could be
 * walked around entirely. That service now derives the recipient server-side,
 * but a retry replays whatever is stored on the row — and rows created before
 * that change can hold any phone number a landlord MANAGER typed into the old
 * endpoint. A platform owner clicking "retry" on such a row, in good faith,
 * would have sent money there.
 *
 * <p>The row is refused rather than corrected: rewriting a recipient on a
 * financial record to make a retry succeed is the silent mutation of money
 * history that this codebase avoids everywhere else.
 */
class DisbursementRetryGuardTest {

    private DisbursementRepository disbursementRepository;
    private DarajaB2CService darajaB2CService;
    private PlatformSettingsService platformSettingsService;
    private TenantRepository tenantRepository;
    private FinancialAuditService financialAuditService;
    private BusinessMetrics metrics;
    private DisbursementRetrySweepService service;

    private final UUID disbursementId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private static final String REGISTERED = "+254711000111";
    private static final String ATTACKER = "+254700999888";

    @BeforeEach
    void setUp() {
        disbursementRepository = mock(DisbursementRepository.class);
        darajaB2CService = mock(DarajaB2CService.class);
        platformSettingsService = mock(PlatformSettingsService.class);
        tenantRepository = mock(TenantRepository.class);
        financialAuditService = mock(FinancialAuditService.class);
        metrics = mock(BusinessMetrics.class);

        service = new DisbursementRetrySweepService(
                disbursementRepository, darajaB2CService, platformSettingsService,
                tenantRepository, financialAuditService, metrics);
    }

    private Disbursement failedDisbursementTo(String recipientPhone) {
        Disbursement d = mock(Disbursement.class);
        when(d.getId()).thenReturn(disbursementId);
        when(d.getTenantId()).thenReturn(tenantId);
        when(d.getStatus()).thenReturn(DisbursementStatus.FAILED);
        when(d.getRetryCount()).thenReturn(0);
        when(d.getAmount()).thenReturn(new BigDecimal("14250"));
        when(d.getRecipientPhone()).thenReturn(recipientPhone);
        when(d.getRecipientName()).thenReturn("Karungwa Properties");
        when(d.getCommandId()).thenReturn("BusinessPayment");
        return d;
    }

    private Tenant landlordWithPayoutPhone(String phone) {
        Tenant t = mock(Tenant.class);
        when(t.getPayoutPhoneNumber()).thenReturn(phone);
        return t;
    }

    private void allowRetries() {
        var settings = mock(com.rentmanager.modules.platformsettings.domain.model.PlatformSettings.class);
        when(settings.getDisbursementMaxRetryAttempts()).thenReturn(3);
        when(platformSettingsService.getEffectiveSettings()).thenReturn(settings);
    }

    // ── The case this guard exists for ───────────────────────────────────

    @Test
    void aRowWhoseRecipientIsNotTheRegisteredPayoutNumberIsRefused() {
        Disbursement legacyRow = failedDisbursementTo(ATTACKER);
        Tenant landlord = landlordWithPayoutPhone(REGISTERED);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(legacyRow));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        allowRetries();

        service.retryOne(disbursementId);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
        verify(legacyRow).markRequiresManualAttention();
        verify(financialAuditService).disbursementRetryRefused(eq(tenantId), eq(disbursementId), any());
    }

    @Test
    void aRefusedRetryIsCountedSoARunOfThemIsVisible() {
        Disbursement legacyRow = failedDisbursementTo(ATTACKER);
        Tenant landlord = landlordWithPayoutPhone(REGISTERED);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(legacyRow));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        allowRetries();

        service.retryOne(disbursementId);

        verify(metrics).disbursementRefused();
    }

    @Test
    void aLandlordWithNoRegisteredPayoutNumberCannotHaveARetryReleased() {
        Disbursement row = failedDisbursementTo(REGISTERED);
        Tenant landlord = landlordWithPayoutPhone(null);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(row));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        allowRetries();

        service.retryOne(disbursementId);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
        verify(financialAuditService).disbursementRetryRefused(eq(tenantId), eq(disbursementId), any());
    }

    /**
     * A missing landlord must not fall through to "send it anyway".
     */
    @Test
    void anUnknownLandlordIsRefused() {
        Disbursement row = failedDisbursementTo(REGISTERED);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(row));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());
        allowRetries();

        service.retryOne(disbursementId);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }

    // ── The legitimate path still works ──────────────────────────────────

    @Test
    void aRowMatchingTheRegisteredNumberIsRetried() {
        Disbursement row = failedDisbursementTo(REGISTERED);
        Tenant landlord = landlordWithPayoutPhone(REGISTERED);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(row));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any())).thenReturn("OCID-R1");
        allowRetries();

        service.retryOne(disbursementId);

        verify(darajaB2CService).initiateB2C(
                eq(new BigDecimal("14250")), eq(REGISTERED), any(), any(), any());
    }

    /**
     * A retry is authorised by a different person at a different time from the
     * original initiation, so it gets its own audit row. Folding it into the
     * original would hide who actually released the money.
     */
    @Test
    void aSuccessfulRetryIsAudited() {
        Disbursement row = failedDisbursementTo(REGISTERED);
        Tenant landlord = landlordWithPayoutPhone(REGISTERED);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(row));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any())).thenReturn("OCID-R2");
        allowRetries();

        service.retryOne(disbursementId);

        verify(financialAuditService).disbursementRetried(eq(tenantId), eq(disbursementId), any(), any());
    }

    // ── Pre-existing guards must survive the change ──────────────────────

    @Test
    void aDisbursementThatIsNotFailedIsNotRetried() {
        Disbursement row = mock(Disbursement.class);
        when(row.getStatus()).thenReturn(DisbursementStatus.SUCCESS);

        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(row));

        service.retryOne(disbursementId);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
        verify(tenantRepository, never()).findById(any());
    }

    @Test
    void aMissingDisbursementIsIgnored() {
        when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.empty());

        service.retryOne(disbursementId);

        verify(darajaB2CService, never()).initiateB2C(any(), any(), any(), any(), any());
    }
}
