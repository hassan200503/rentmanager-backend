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
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
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
    private TenantRepository tenantRepository;
    @Mock
    private com.rentmanager.modules.deposit.application.service.DepositCommandService depositCommandService;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private Lease lease;
    @Mock
    private Tenant tenant;

    private RentLedgerApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RentLedgerApplicationService(
                rentLedgerEntryRepository, rentTransactionRepository, leaseRepository, tenantRepository,
                depositCommandService, eventPublisher
        );

        // Every test in this class works with a single fixed currency —
        // resolveCurrency(tenantId) always returns it via this stub, so
        // individual tests don't need to think about it.
        lenient().when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        lenient().when(tenant.getCurrency()).thenReturn("KES");

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
    class PostDeposit {

        private final LocalDate fullMonthStart = LocalDate.of(2026, 4, 1);

        @Test
        void bootstrapsLedgerEntryPostsAuditOnlyDepositTransactionAndRecordsDeposit() {
            when(rentTransactionRepository.findByLease(tenantId, leaseId)).thenReturn(List.of());
            when(rentLedgerEntryRepository.findByLease(tenantId, leaseId)).thenReturn(List.of());
            when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
            when(lease.getStartDate()).thenReturn(fullMonthStart);
            when(lease.getRentAmount()).thenReturn(new BigDecimal("1000.00"));
            when(lease.getUnitId()).thenReturn(unitId);
            when(lease.getTenantProfileId()).thenReturn(tenantProfileId);

            service.postDeposit(tenantId, "corr", leaseId, new BigDecimal("1000.00"), "MPESA-DEP-1");

            ArgumentCaptor<RentTransaction> txCaptor = ArgumentCaptor.forClass(RentTransaction.class);
            verify(rentTransactionRepository, times(2)).save(txCaptor.capture()); // RENT_CHARGE bootstrap, then DEPOSIT
            List<RentTransaction> saved = txCaptor.getAllValues();
            assertThat(saved.get(0).getType()).isEqualTo(RentTransactionType.RENT_CHARGE);
            assertThat(saved.get(1).getType()).isEqualTo(RentTransactionType.DEPOSIT);
            assertThat(saved.get(1).getExternalReference()).isEqualTo("MPESA-DEP-1");

            ArgumentCaptor<RentLedgerEntry> entryCaptor = ArgumentCaptor.forClass(RentLedgerEntry.class);
            verify(rentLedgerEntryRepository).save(entryCaptor.capture());
            // The deposit must NOT have moved amountPaid — it's audit-only now (see
            // RentTransaction.reducesBalanceOwed()'s javadoc).
            assertThat(entryCaptor.getValue().getAmountPaid()).isEqualByComparingTo("0.00");

            verify(depositCommandService).recordAlreadyCollectedDeposit(
                    eq(tenantId), eq(leaseId), eq(unitId), eq(tenantProfileId), eq(new BigDecimal("1000.00")), eq("corr")
            );
        }

        @Test
        void reusesExistingLedgerEntryWithoutRecreatingRentCharge() {
            RentLedgerEntry existing = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    fullMonthStart, fullMonthStart.plusDays(29), fullMonthStart, new BigDecimal("1000.00"), false
            );
            existing.pullDomainEvents();
            when(rentTransactionRepository.findByLease(tenantId, leaseId)).thenReturn(List.of());
            when(rentLedgerEntryRepository.findByLease(tenantId, leaseId)).thenReturn(List.of(existing));

            service.postDeposit(tenantId, "corr", leaseId, new BigDecimal("500.00"), null);

            verifyNoInteractions(leaseRepository);
            verify(rentTransactionRepository, times(1)).save(any(RentTransaction.class)); // only the DEPOSIT row
            verify(depositCommandService).recordAlreadyCollectedDeposit(
                    eq(tenantId), eq(leaseId), eq(unitId), eq(tenantProfileId), eq(new BigDecimal("500.00")), eq("corr")
            );
        }

        @Test
        void isNoOpWhenDepositAlreadyRecorded() {
            RentTransaction existingDeposit = RentTransaction.create(
                    tenantId, UUID.randomUUID(), leaseId, RentTransactionType.DEPOSIT,
                    new BigDecimal("500.00"), "MPESA-1", RentTransactionSource.MPESA, "SYSTEM", LocalDateTime.now()
            );
            when(rentTransactionRepository.findByLease(tenantId, leaseId)).thenReturn(List.of(existingDeposit));

            service.postDeposit(tenantId, "corr", leaseId, new BigDecimal("500.00"), "MPESA-1");

            verifyNoInteractions(leaseRepository, depositCommandService);
            verify(rentLedgerEntryRepository, never()).findByLease(any(), any());
        }

        @Test
        void concurrentDuplicateSaveDoesNotRecordDeposit() {
            RentLedgerEntry existing = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    fullMonthStart, fullMonthStart.plusDays(29), fullMonthStart, new BigDecimal("1000.00"), false
            );
            existing.pullDomainEvents();
            when(rentTransactionRepository.findByLease(tenantId, leaseId)).thenReturn(List.of());
            when(rentLedgerEntryRepository.findByLease(tenantId, leaseId)).thenReturn(List.of(existing));
            when(rentTransactionRepository.save(any(RentTransaction.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate external_reference"));

            service.postDeposit(tenantId, "corr", leaseId, new BigDecimal("500.00"), "MPESA-1");

            verifyNoInteractions(depositCommandService);
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

    @Nested
    class ReverseTransaction {

        private RentLedgerEntry entry;
        private RentTransaction payment;

        @BeforeEach
        void makeEntryWithPayment() {
            entry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                    new BigDecimal("1000.00"), false
            );
            entry.pullDomainEvents();
            payment = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
            );
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();
        }

        @Test
        void postsCompensatingReversalAndKeepsBothRows() {
            when(rentTransactionRepository.findByIdAndTenantId(payment.getId(), tenantId))
                    .thenReturn(Optional.of(payment));
            when(rentTransactionRepository.findByReversesTransactionId(tenantId, payment.getId()))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            service.reverseTransaction(tenantId, payment.getId(), "admin-2");

            assertThat(entry.getAmountPaid()).isEqualByComparingTo("0.00");

            ArgumentCaptor<RentTransaction> txCaptor = ArgumentCaptor.forClass(RentTransaction.class);
            verify(rentTransactionRepository).save(txCaptor.capture());
            RentTransaction reversal = txCaptor.getValue();
            assertThat(reversal.getType()).isEqualTo(RentTransactionType.REVERSAL);
            assertThat(reversal.getReversesTransactionId()).isEqualTo(payment.getId());
            assertThat(reversal.getAmount()).isEqualByComparingTo("400.00");
            assertThat(reversal.getRecordedBy()).isEqualTo("admin-2");

            // The original is never deleted — no delete method even exists on the port anymore.
            verify(rentLedgerEntryRepository).save(entry);
        }

        @Test
        void isNoOpWhenAlreadyReversed() {
            RentTransaction existingReversal = RentTransaction.reversalOf(payment, tenantId, "admin-2", LocalDateTime.now());
            when(rentTransactionRepository.findByIdAndTenantId(payment.getId(), tenantId))
                    .thenReturn(Optional.of(payment));
            when(rentTransactionRepository.findByReversesTransactionId(tenantId, payment.getId()))
                    .thenReturn(Optional.of(existingReversal));

            service.reverseTransaction(tenantId, payment.getId(), "admin-2");

            verify(rentTransactionRepository, never()).save(any());
            verify(rentLedgerEntryRepository, never()).save(any());
        }

        @Test
        void treatsConcurrentDuplicateReversalAsNoOpNotError() {
            when(rentTransactionRepository.findByIdAndTenantId(payment.getId(), tenantId))
                    .thenReturn(Optional.of(payment));
            when(rentTransactionRepository.findByReversesTransactionId(tenantId, payment.getId()))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));
            when(rentTransactionRepository.save(any(RentTransaction.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate reverses_transaction_id"));

            service.reverseTransaction(tenantId, payment.getId(), "admin-2");

            verify(rentLedgerEntryRepository, never()).save(any(RentLedgerEntry.class));
        }

        @Test
        void throwsWhenTransactionNotFound() {
            UUID missingId = UUID.randomUUID();
            when(rentTransactionRepository.findByIdAndTenantId(missingId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reverseTransaction(tenantId, missingId, "admin-2"))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_NOT_FOUND);
        }

        @Test
        void throwsWhenLedgerEntryNotFound() {
            when(rentTransactionRepository.findByIdAndTenantId(payment.getId(), tenantId))
                    .thenReturn(Optional.of(payment));
            when(rentTransactionRepository.findByReversesTransactionId(tenantId, payment.getId()))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reverseTransaction(tenantId, payment.getId(), "admin-2"))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        void rejectsReversingRentCharge() {
            RentLedgerEntry freshEntry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), LocalDate.of(2026, 5, 1),
                    new BigDecimal("1000.00"), false
            );
            freshEntry.pullDomainEvents();
            RentTransaction charge = RentTransaction.create(
                    tenantId, freshEntry.getId(), leaseId, RentTransactionType.RENT_CHARGE,
                    new BigDecimal("1000.00"), null, RentTransactionSource.SYSTEM, "SYSTEM", LocalDateTime.now()
            );
            when(rentTransactionRepository.findByIdAndTenantId(charge.getId(), tenantId))
                    .thenReturn(Optional.of(charge));
            when(rentTransactionRepository.findByReversesTransactionId(tenantId, charge.getId()))
                    .thenReturn(Optional.empty());
            when(rentLedgerEntryRepository.findByIdAndTenantId(freshEntry.getId(), tenantId))
                    .thenReturn(Optional.of(freshEntry));

            assertThatThrownBy(() -> service.reverseTransaction(tenantId, charge.getId(), "admin-2"))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }
    }
}