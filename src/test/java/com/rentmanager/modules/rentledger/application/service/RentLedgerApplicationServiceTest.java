package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentLedgerApplicationServiceTest {

    @Mock
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Mock
    private RentTransactionRepository rentTransactionRepository;
    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private Lease lease;

    private RentLedgerApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RentLedgerApplicationService(
                rentLedgerEntryRepository, rentTransactionRepository, leaseRepository, eventPublisher
        );

        // Repository .save(...) calls in the service are treated as returning
        // whatever was passed in, mirroring how a real save-then-return adapter
        // behaves — this lets domain events registered on the in-memory object
        // survive through to the service's later pullDomainEvents() call.
        lenient().when(rentLedgerEntryRepository.save(any(RentLedgerEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(rentTransactionRepository.save(any(RentTransaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    class PostCharge {

        private final LocalDate fullMonthStart = LocalDate.of(2026, 4, 1);
        private final LocalDate fullMonthEnd = LocalDate.of(2026, 4, 30);
        private final LocalDate dueDate = LocalDate.of(2026, 4, 1);

        @Test
        void isNoOpWhenEntryAlreadyExistsForPeriod() {
            RentLedgerEntry existing = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    fullMonthStart, fullMonthEnd, dueDate, new BigDecimal("1000.00"), false
            );
            when(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, fullMonthStart))
                    .thenReturn(Optional.of(existing));

            RentLedgerEntry result = service.postCharge(
                    tenantId, "corr", leaseId, fullMonthStart, fullMonthEnd, dueDate
            );

            assertThat(result).isSameAs(existing);
            verifyNoInteractions(leaseRepository);
            verify(rentLedgerEntryRepository, never()).save(any());
            verify(rentTransactionRepository, never()).save(any());
        }

        @Test
        void throwsWhenLeaseNotFound() {
            when(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, fullMonthStart))
                    .thenReturn(Optional.empty());
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.postCharge(
                    tenantId, "corr", leaseId, fullMonthStart, fullMonthEnd, dueDate
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.LEASE_NOT_FOUND);
        }

        @Test
        void postsFullMonthChargeWithoutProrationWhenStartIsFirstOfMonth() {
            when(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, fullMonthStart))
                    .thenReturn(Optional.empty());
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));
            when(lease.getStartDate()).thenReturn(fullMonthStart); // dayOfMonth == 1 -> not prorated
            when(lease.getRentAmount()).thenReturn(new BigDecimal("1000.00"));
            when(lease.getUnitId()).thenReturn(unitId);
            when(lease.getTenantProfileId()).thenReturn(tenantProfileId);

            RentLedgerEntry result = service.postCharge(
                    tenantId, "corr", leaseId, fullMonthStart, fullMonthEnd, dueDate
            );

            assertThat(result.isProrated()).isFalse();
            assertThat(result.getAmountDue()).isEqualByComparingTo("1000.00");

            ArgumentCaptor<RentTransaction> txCaptor = ArgumentCaptor.forClass(RentTransaction.class);
            verify(rentTransactionRepository).save(txCaptor.capture());
            assertThat(txCaptor.getValue().getType()).isEqualTo(RentTransactionType.RENT_CHARGE);
            assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("1000.00");
            assertThat(txCaptor.getValue().getSource()).isEqualTo(RentTransactionSource.SYSTEM);

            verify(eventPublisher).publishAll(anyList());
        }

        @Test
        void proratesFirstPeriodWhenLeaseStartsMidMonth() {
            LocalDate midMonthStart = LocalDate.of(2026, 3, 15); // 31-day month
            LocalDate midMonthEnd = LocalDate.of(2026, 3, 31);

            when(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, midMonthStart))
                    .thenReturn(Optional.empty());
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));
            when(lease.getStartDate()).thenReturn(midMonthStart); // matches billingPeriodStart, day > 1
            when(lease.getRentAmount()).thenReturn(new BigDecimal("1000.00"));
            when(lease.getUnitId()).thenReturn(unitId);
            when(lease.getTenantProfileId()).thenReturn(tenantProfileId);

            RentLedgerEntry result = service.postCharge(
                    tenantId, "corr", leaseId, midMonthStart, midMonthEnd, midMonthStart
            );

            // 17 occupied days (15th-31st inclusive) of 31 -> 1000 * 17/31 = 548.387... -> 548.39
            assertThat(result.isProrated()).isTrue();
            assertThat(result.getAmountDue()).isEqualByComparingTo("548.39");
        }

        @Test
        void doesNotProrateWhenBillingPeriodStartDiffersFromLeaseStart() {
            // Recurring period, not the lease's opening period — full charge regardless of day-of-month.
            LocalDate recurringStart = LocalDate.of(2026, 5, 15);
            LocalDate recurringEnd = LocalDate.of(2026, 5, 31);

            when(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, recurringStart))
                    .thenReturn(Optional.empty());
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                    .thenReturn(Optional.of(lease));
            when(lease.getStartDate()).thenReturn(LocalDate.of(2026, 3, 15)); // different from billingPeriodStart
            when(lease.getRentAmount()).thenReturn(new BigDecimal("1000.00"));
            when(lease.getUnitId()).thenReturn(unitId);
            when(lease.getTenantProfileId()).thenReturn(tenantProfileId);

            RentLedgerEntry result = service.postCharge(
                    tenantId, "corr", leaseId, recurringStart, recurringEnd, recurringStart
            );

            assertThat(result.isProrated()).isFalse();
            assertThat(result.getAmountDue()).isEqualByComparingTo("1000.00");
        }
    }

    @Nested
    class ApplyTransaction {

        private RentLedgerEntry entry;
        private final UUID ledgerEntryId = UUID.randomUUID();

        @BeforeEach
        void makeEntry() {
            entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                    new BigDecimal("1000.00"), false
            );
            entry.pullDomainEvents(); // drain RentDuePosted, not under test here
        }

        @Test
        void appliesPaymentAndPublishesEvents() {
            when(rentTransactionRepository.findByExternalReference(tenantId, "MPESA-1"))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            RentLedgerEntry result = service.applyTransaction(
                    tenantId, "corr", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            assertThat(result.getAmountPaid()).isEqualByComparingTo("400.00");
            verify(eventPublisher).publishAll(anyList());
        }

        @Test
        void isNoOpWhenExternalReferenceAlreadyRecorded() {
            RentTransaction duplicate = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );
            when(rentTransactionRepository.findByExternalReference(tenantId, "MPESA-1"))
                    .thenReturn(Optional.of(duplicate));
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            RentLedgerEntry result = service.applyTransaction(
                    tenantId, "corr", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            assertThat(result.getAmountPaid()).isEqualByComparingTo("0.00"); // untouched
            verify(rentTransactionRepository, never()).save(any());
            verify(eventPublisher, never()).publishAll(anyList());
        }

        @Test
        void treatsConcurrentDuplicateSaveAsSuccessNotError() {
            when(rentTransactionRepository.findByExternalReference(tenantId, "MPESA-1"))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));
            when(rentTransactionRepository.save(any(RentTransaction.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate external_reference"));

            RentLedgerEntry result = service.applyTransaction(
                    tenantId, "corr", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            assertThat(result).isNotNull();
            verify(rentLedgerEntryRepository, never()).save(any(RentLedgerEntry.class));
            verify(eventPublisher, never()).publishAll(anyList());
            // Confirms the race is absorbed, not propagated as a 500.
        }

        @Test
        void throwsWhenLedgerEntryNotFound() {
            when(rentTransactionRepository.findByExternalReference(any(), any()))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.applyTransaction(
                    tenantId, "corr", ledgerEntryId, RentTransactionType.PAYMENT,
                    BigDecimal.TEN, "ref", RentTransactionSource.MPESA, "system", LocalDateTime.now()
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    class ApplyAdjustment {

        private RentLedgerEntry entry;

        @BeforeEach
        void makeEntry() {
            entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                    new BigDecimal("1000.00"), false
            );
            entry.pullDomainEvents();
        }

        @Test
        void positiveDeltaIncreasesAmountDueViaService() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            RentLedgerEntry result = service.applyAdjustment(
                    tenantId, "corr", entry.getId(), new BigDecimal("100.00"), "admin-1", LocalDateTime.now()
            );

            assertThat(result.getAmountDue()).isEqualByComparingTo("1100.00");

            ArgumentCaptor<RentTransaction> txCaptor = ArgumentCaptor.forClass(RentTransaction.class);
            verify(rentTransactionRepository).save(txCaptor.capture());
            // amount stored positive regardless of delta's sign, per RentTransaction's contract
            assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("100.00");
            assertThat(txCaptor.getValue().getType()).isEqualTo(RentTransactionType.ADJUSTMENT);
        }
    }

    @Nested
    class MarkOverdue {

        @Test
        void transitionsEntryAndPublishes() {
            RentLedgerEntry entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                    new BigDecimal("1000.00"), false
            );
            entry.pullDomainEvents();
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            RentLedgerEntry result = service.markOverdue(tenantId, "corr", entry.getId(), 5);

            assertThat(result.getStatus().name()).isEqualTo("OVERDUE");
            verify(eventPublisher).publishAll(anyList());
        }
    }

    @Nested
    class OverpaymentResolution {

        private RentLedgerEntry overpaidEntry;

        @BeforeEach
        void makeOverpaid() {
            overpaidEntry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                    new BigDecimal("1000.00"), false
            );
            overpaidEntry.pullDomainEvents();
            RentTransaction overpay = RentTransaction.create(
                    tenantId, overpaidEntry.getId(), leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("1200.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
            );
            overpaidEntry.applyTransaction("corr", overpay);
            overpaidEntry.pullDomainEvents();
        }

        @Test
        void refundResolvesToPaidAndDoesNotPublish() {
            when(rentLedgerEntryRepository.findByIdAndTenantId(overpaidEntry.getId(), tenantId))
                    .thenReturn(Optional.of(overpaidEntry));

            RentLedgerEntry result = service.resolveOverpaymentWithRefund(
                    tenantId, overpaidEntry.getId(), new BigDecimal("200.00"),
                    "refund-ref", RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
            );

            assertThat(result.getStatus().name()).isEqualTo("PAID");
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void creditResolutionAppliesToTargetAndPublishesOnlyForTarget() {
            RentLedgerEntry targetEntry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1),
                    new BigDecimal("1000.00"), false
            );
            targetEntry.pullDomainEvents();

            when(rentLedgerEntryRepository.findByIdAndTenantId(overpaidEntry.getId(), tenantId))
                    .thenReturn(Optional.of(overpaidEntry));
            when(rentLedgerEntryRepository.findByIdAndTenantId(targetEntry.getId(), tenantId))
                    .thenReturn(Optional.of(targetEntry));

            service.resolveOverpaymentAsCredit(
                    tenantId, "corr", overpaidEntry.getId(), targetEntry.getId(), "admin-1", LocalDateTime.now()
            );

            assertThat(targetEntry.getAmountPaid()).isEqualByComparingTo("200.00");
            assertThat(overpaidEntry.getStatus().name()).isEqualTo("PAID");

            verify(eventPublisher, times(1)).publishAll(anyList());
        }
    }
}