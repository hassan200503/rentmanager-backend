package com.rentmanager.modules.deposit.application.service;

import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DepositCommandServiceTest {

    private DepositRepository depositRepository;
    private TenantRepository tenantRepository;
    private DomainEventPublisher eventPublisher;
    private Tenant tenant;
    private DepositCommandService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        depositRepository = mock(DepositRepository.class);
        tenantRepository = mock(TenantRepository.class);
        eventPublisher = mock(DomainEventPublisher.class);
        tenant = mock(Tenant.class);
        service = new DepositCommandService(depositRepository, tenantRepository, eventPublisher);

        when(depositRepository.save(any(Deposit.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class CreateDeposit {

        @Test
        void createsUnpaidDepositUsingTenantCurrencyAndPublishesEvent() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenant.getCurrency()).thenReturn("USD");

            Deposit result = service.createDeposit(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"));

            assertThat(result.getStatus()).isEqualTo(DepositStatus.UNPAID);
            assertThat(result.getCurrency()).isEqualTo("USD");
            verify(eventPublisher).publishAll(anyList());
        }

        @Test
        void fallsBackToKesWhenTenantHasNoCurrency() {
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            Deposit result = service.createDeposit(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"));

            assertThat(result.getCurrency()).isEqualTo("KES");
        }
    }

    @Nested
    class ConfirmPayment {

        @Test
        void movesToHeldAndPublishes() {
            Deposit unpaid = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr", "KES");
            when(depositRepository.findByIdAndTenantId(unpaid.getId(), tenantId)).thenReturn(Optional.of(unpaid));

            Deposit result = service.confirmPayment(tenantId, unpaid.getId(), new BigDecimal("1000.00"));

            assertThat(result.getStatus()).isEqualTo(DepositStatus.HELD);
            verify(eventPublisher).publishAll(anyList());
        }

        @Test
        void throwsWhenNotFound() {
            UUID missingId = UUID.randomUUID();
            when(depositRepository.findByIdAndTenantId(missingId, tenantId)).thenReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.confirmPayment(tenantId, missingId, BigDecimal.TEN))
                    .isInstanceOf(com.rentmanager.shared.exception.ResourceNotFoundException.class);
        }
    }

    @Nested
    class RefundAndForfeit {

        private Deposit heldDeposit() {
            Deposit deposit = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr", "KES");
            deposit.confirmPayment(new BigDecimal("1000.00"), "corr");
            deposit.pullDomainEvents();
            return deposit;
        }

        @Test
        void refundMovesToRefundedAndPublishes() {
            Deposit deposit = heldDeposit();
            when(depositRepository.findByIdAndTenantId(deposit.getId(), tenantId)).thenReturn(Optional.of(deposit));

            Deposit result = service.refundDeposit(tenantId, deposit.getId(), new BigDecimal("1000.00"));

            assertThat(result.getStatus()).isEqualTo(DepositStatus.REFUNDED);
            verify(eventPublisher).publishAll(anyList());
        }

        @Test
        void forfeitMovesToForfeitedAndPublishes() {
            Deposit deposit = heldDeposit();
            when(depositRepository.findByIdAndTenantId(deposit.getId(), tenantId)).thenReturn(Optional.of(deposit));

            Deposit result = service.forfeitDeposit(tenantId, deposit.getId());

            assertThat(result.getStatus()).isEqualTo(DepositStatus.FORFEITED);
            verify(eventPublisher).publishAll(anyList());
        }
    }

    @Nested
    class RecordAlreadyCollectedDeposit {

        @Test
        void createsAHeldDepositWithoutPublishingAnyEvent() {
            when(depositRepository.findByLeaseId(leaseId)).thenReturn(Optional.empty());
            when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

            Deposit result = service.recordAlreadyCollectedDeposit(
                    tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr-reservation"
            );

            assertThat(result.getStatus()).isEqualTo(DepositStatus.HELD);
            assertThat(result.getAmountPaid()).isEqualByComparingTo("1000.00");
            // The whole point: this must NEVER publish DepositPaidEvent, or it
            // would fire DepositPaymentEventListener and race the real
            // activation flow (ReservationFulfillmentOrchestrator /
            // LeaseActivationOrchestrator) — see the method's javadoc.
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void isIdempotentPerLease() {
            Deposit existing = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr", "KES");
            when(depositRepository.findByLeaseId(leaseId)).thenReturn(Optional.of(existing));

            Deposit result = service.recordAlreadyCollectedDeposit(
                    tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr-reservation"
            );

            assertThat(result).isSameAs(existing);
            verify(depositRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }
    }
}
