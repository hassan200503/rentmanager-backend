package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommissionPolicyServiceTest {

    @Mock
    private CommissionPolicyRepository commissionPolicyRepository;
    @Mock
    private FinancialAuditService financialAuditService;

    private CommissionPolicyService service;

    private final UUID landlordOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CommissionPolicyService(commissionPolicyRepository, financialAuditService);
    }

    @Nested
    class GetActiveRate {

        private final BigDecimal defaultRate = new BigDecimal("5.00");
        private final BigDecimal overrideRate = new BigDecimal("3.50");

        @Test
        void returnsLandlordOverrideWhenPresent() {
            CommissionPolicy override = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), null, landlordOrgId, overrideRate,
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(override));

            BigDecimal result = service.getActiveRate(landlordOrgId);

            assertThat(result).isEqualByComparingTo(overrideRate);
            verify(commissionPolicyRepository, never()).findActiveDefault();
        }

        @Test
        void fallsBackToDefaultWhenNoLandlordOverride() {
            CommissionPolicy defaultPolicy = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), null, null, defaultRate,
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.empty());
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.of(defaultPolicy));

            BigDecimal result = service.getActiveRate(landlordOrgId);

            assertThat(result).isEqualByComparingTo(defaultRate);
        }

        @Test
        void returnsNullWhenNoPolicyExists() {
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.empty());
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.empty());

            BigDecimal result = service.getActiveRate(landlordOrgId);

            assertThat(result).isNull();
        }

        @Test
        void usesDefaultWhenLandlordArgIsNull() {
            CommissionPolicy defaultPolicy = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), null, null, defaultRate,
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.of(defaultPolicy));

            BigDecimal result = service.getActiveRate(null);

            assertThat(result).isEqualByComparingTo(defaultRate);
            verify(commissionPolicyRepository, never()).findActiveByLandlordOrgId(any());
        }

        @Test
        void returnsNullWhenLandlordIsNullAndNoDefault() {
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.empty());

            BigDecimal result = service.getActiveRate(null);

            assertThat(result).isNull();
        }

        @Test
        void picksCurrentlyActiveWhenMultipleHistoricalRowsExist() {
            CommissionPolicy oldOverride = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 0L, landlordOrgId, new BigDecimal("2.00"),
                    Instant.now().minusSeconds(86400), false, "admin",
                    Instant.now().minusSeconds(86400), Instant.now().minusSeconds(3600)
            );
            CommissionPolicy currentOverride = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, landlordOrgId, overrideRate,
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );

            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(currentOverride));

            BigDecimal result = service.getActiveRate(landlordOrgId);

            assertThat(result).isEqualByComparingTo(overrideRate);
            assertThat(oldOverride.isActive()).isFalse();
            assertThat(currentOverride.isActive()).isTrue();
        }
    }

    @Nested
    class SetDefaultRate {

        private final BigDecimal rate = new BigDecimal("5.00");

        @Test
        void createsNewPolicyWhenNoExistingDefault() {
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.empty());
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CommissionPolicy result = service.setDefaultRate(rate, Instant.now(), "admin");

            assertThat(result.getRatePercent()).isEqualByComparingTo(rate);
            assertThat(result.getLandlordOrgId()).isNull();
            assertThat(result.isActive()).isTrue();
            assertThat(result.getCreatedBy()).isEqualTo("admin");
        }

        @Test
        void deactivatesOldDefaultBeforeSavingNew() {
            CommissionPolicy oldPolicy = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, null, new BigDecimal("3.00"),
                    Instant.now().minusSeconds(86400), true, "admin",
                    Instant.now().minusSeconds(86400), Instant.now()
            );
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.of(oldPolicy));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CommissionPolicy result = service.setDefaultRate(rate, Instant.now(), "admin");

            assertThat(oldPolicy.isActive()).isFalse();
            verify(commissionPolicyRepository).save(oldPolicy);
            assertThat(result.getRatePercent()).isEqualByComparingTo(rate);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        void oldRowStillReadableWithOriginalValuesAfterDeactivation() {
            BigDecimal oldRate = new BigDecimal("3.00");
            CommissionPolicy oldPolicy = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, null, oldRate,
                    Instant.now().minusSeconds(86400), true, "admin",
                    Instant.now().minusSeconds(86400), Instant.now()
            );
            when(commissionPolicyRepository.findActiveDefault())
                    .thenReturn(Optional.of(oldPolicy));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setDefaultRate(rate, Instant.now(), "admin");

            assertThat(oldPolicy.getRatePercent()).isEqualByComparingTo(oldRate);
            assertThat(oldPolicy.getCreatedBy()).isEqualTo("admin");
        }
    }

    @Nested
    class SetLandlordRate {

        private final BigDecimal rate = new BigDecimal("3.50");

        @Test
        void createsNewOverrideWhenNoActiveOverride() {
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.empty());
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CommissionPolicy result = service.setLandlordRate(landlordOrgId, rate, Instant.now(), "admin");

            assertThat(result.getRatePercent()).isEqualByComparingTo(rate);
            assertThat(result.getLandlordOrgId()).isEqualTo(landlordOrgId);
            assertThat(result.isActive()).isTrue();
        }

        @Test
        void deactivatesOldOverrideBeforeSavingNew() {
            CommissionPolicy oldOverride = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, landlordOrgId, new BigDecimal("2.00"),
                    Instant.now().minusSeconds(86400), true, "admin",
                    Instant.now().minusSeconds(86400), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(oldOverride));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setLandlordRate(landlordOrgId, rate, Instant.now(), "admin");

            assertThat(oldOverride.isActive()).isFalse();
            verify(commissionPolicyRepository).save(oldOverride);
        }

        @Test
        void clearsOverriddenFlagOnOldPolicyNotMutatesValues() {
            BigDecimal oldRate = new BigDecimal("2.00");
            CommissionPolicy oldOverride = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, landlordOrgId, oldRate,
                    Instant.now().minusSeconds(86400), true, "admin",
                    Instant.now().minusSeconds(86400), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(oldOverride));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.setLandlordRate(landlordOrgId, rate, Instant.now(), "admin");

            assertThat(oldOverride.getRatePercent()).isEqualByComparingTo(oldRate);
            assertThat(oldOverride.getLandlordOrgId()).isEqualTo(landlordOrgId);
        }
    }

    @Nested
    class ComputeCommission {

        @Test
        void computesPercentageCorrectly() {
            BigDecimal gross = new BigDecimal("1000.00");
            BigDecimal rate = new BigDecimal("5.00");

            BigDecimal result = CommissionPolicyService.computeCommission(gross, rate);

            assertThat(result).isEqualByComparingTo("50.00");
        }

        @Test
        void roundsHalfUp() {
            BigDecimal gross = new BigDecimal("100.55");
            BigDecimal rate = new BigDecimal("3.00");

            BigDecimal result = CommissionPolicyService.computeCommission(gross, rate);

            assertThat(result).isEqualByComparingTo("3.02");
        }

        @Test
        void returnsZeroForNullGross() {
            assertThat(CommissionPolicyService.computeCommission(null, new BigDecimal("5.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void returnsZeroForNullRate() {
            assertThat(CommissionPolicyService.computeCommission(new BigDecimal("1000.00"), null))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    class ComputeNetAmount {

        @Test
        void subtractsCommissionFromGross() {
            BigDecimal gross = new BigDecimal("1000.00");
            BigDecimal commission = new BigDecimal("50.00");

            BigDecimal result = CommissionPolicyService.computeNetAmount(gross, commission);

            assertThat(result).isEqualByComparingTo("950.00");
        }

        @Test
        void returnsZeroWhenCommissionExceedsGross() {
            BigDecimal gross = new BigDecimal("100.00");
            BigDecimal commission = new BigDecimal("150.00");

            BigDecimal result = CommissionPolicyService.computeNetAmount(gross, commission);

            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void returnsGrossWhenCommissionIsNull() {
            BigDecimal gross = new BigDecimal("1000.00");

            BigDecimal result = CommissionPolicyService.computeNetAmount(gross, null);

            assertThat(result).isEqualByComparingTo(gross);
        }

        @Test
        void returnsZeroForNullGross() {
            assertThat(CommissionPolicyService.computeNetAmount(null, new BigDecimal("50.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
    @Nested
    class ClearLandlordRate {

        @Test
        void deactivatesActiveOverrideWhenPresent() {
            CommissionPolicy override = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, landlordOrgId, new BigDecimal("3.50"),
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(override));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.clearLandlordRate(landlordOrgId);

            assertThat(override.isActive()).isFalse();
            verify(commissionPolicyRepository).save(override);
        }

        @Test
        void isNoOpWhenNoActiveOverrideExists() {
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.empty());

            service.clearLandlordRate(landlordOrgId);

            verify(commissionPolicyRepository, never()).save(any());
        }

        @Test
        void keepsOriginalRateOnDeactivatedRow() {
            CommissionPolicy override = CommissionPolicy.rehydrate(
                    UUID.randomUUID(), 1L, landlordOrgId, new BigDecimal("7.00"),
                    Instant.now(), true, "admin",
                    Instant.now(), Instant.now()
            );
            when(commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId))
                    .thenReturn(Optional.of(override));
            when(commissionPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.clearLandlordRate(landlordOrgId);

            assertThat(override.getRatePercent()).isEqualByComparingTo("7.00");
            assertThat(override.isActive()).isFalse();
        }
    }
}
