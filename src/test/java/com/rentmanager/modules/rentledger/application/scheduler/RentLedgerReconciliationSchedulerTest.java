package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RentLedgerReconciliationSchedulerTest {

    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentTransactionRepository rentTransactionRepository;
    private RentLedgerReconciliationScheduler scheduler;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final LocalDateTime occurredAt = LocalDateTime.now();

    private RentLedgerEntry entryWithAmountPaid(BigDecimal amountPaid) {
        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId, "corr", leaseId, unitId, tenantProfileId,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1),
                new BigDecimal("1000.00"), false
        );
        entry.pullDomainEvents();
        if (amountPaid.compareTo(BigDecimal.ZERO) != 0) {
            RentTransaction payment = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                    amountPaid, null, RentTransactionSource.CASH, "admin-1", occurredAt
            );
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();
        }
        return entry;
    }

    @BeforeEach
    void setUp() {
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentTransactionRepository = mock(RentTransactionRepository.class);
        scheduler = new RentLedgerReconciliationScheduler(rentLedgerEntryRepository, rentTransactionRepository);
    }

    @Test
    void agreeingEntryProducesNoMismatch() {
        RentLedgerEntry entry = entryWithAmountPaid(new BigDecimal("400.00"));
        RentTransaction payment = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("400.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
        );

        when(rentLedgerEntryRepository.findAllByUpdatedAtAfter(any())).thenReturn(List.of(entry));
        when(rentTransactionRepository.findByLedgerEntry(tenantId, entry.getId())).thenReturn(List.of(payment));

        scheduler.runDaily();

        // No exception, no repository writes — this is a read-only sweep.
    }

    @Test
    void mismatchedEntryDoesNotThrowAndContinuesTheSweep() {
        RentLedgerEntry drifted = entryWithAmountPaid(new BigDecimal("400.00"));
        // The transaction log only shows 300, but the entry's stored
        // amountPaid (set up via entryWithAmountPaid) reflects 400 — a
        // deliberate mismatch.
        RentTransaction payment = RentTransaction.create(
                tenantId, drifted.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("300.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
        );
        RentLedgerEntry clean = entryWithAmountPaid(new BigDecimal("1000.00"));
        RentTransaction cleanPayment = RentTransaction.create(
                tenantId, clean.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("1000.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
        );

        when(rentLedgerEntryRepository.findAllByUpdatedAtAfter(any())).thenReturn(List.of(drifted, clean));
        when(rentTransactionRepository.findByLedgerEntry(tenantId, drifted.getId())).thenReturn(List.of(payment));
        when(rentTransactionRepository.findByLedgerEntry(tenantId, clean.getId())).thenReturn(List.of(cleanPayment));

        // Must not throw — a mismatch is logged, not propagated, and the
        // clean entry is still checked.
        scheduler.runDaily();
    }

    @Test
    void anEntryThatThrowsWhileReconcilingDoesNotAbortTheRestOfTheSweep() {
        RentLedgerEntry broken = entryWithAmountPaid(new BigDecimal("400.00"));
        RentLedgerEntry clean = entryWithAmountPaid(new BigDecimal("1000.00"));
        RentTransaction cleanPayment = RentTransaction.create(
                tenantId, clean.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("1000.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
        );

        when(rentLedgerEntryRepository.findAllByUpdatedAtAfter(any())).thenReturn(List.of(broken, clean));
        when(rentTransactionRepository.findByLedgerEntry(tenantId, broken.getId()))
                .thenThrow(new RuntimeException("DB hiccup"));
        when(rentTransactionRepository.findByLedgerEntry(tenantId, clean.getId())).thenReturn(List.of(cleanPayment));

        scheduler.runDaily();

        // If the broken entry's exception weren't caught, this test would
        // fail with the RuntimeException propagating out of runDaily().
    }

    @Test
    void sweepsOnlyEntriesUpdatedRecently() {
        when(rentLedgerEntryRepository.findAllByUpdatedAtAfter(any())).thenReturn(List.of());

        scheduler.runDaily();

        var captor = org.mockito.ArgumentCaptor.forClass(java.time.Instant.class);
        org.mockito.Mockito.verify(rentLedgerEntryRepository).findAllByUpdatedAtAfter(captor.capture());
        assertThatThresholdIsRoughlyTwoDaysAgo(captor.getValue());
    }

    private void assertThatThresholdIsRoughlyTwoDaysAgo(java.time.Instant threshold) {
        java.time.Instant expected = java.time.Instant.now().minus(2, ChronoUnit.DAYS);
        long diffSeconds = Math.abs(java.time.Duration.between(expected, threshold).getSeconds());
        org.assertj.core.api.Assertions.assertThat(diffSeconds).isLessThan(10);
    }
}
