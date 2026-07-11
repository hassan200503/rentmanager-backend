package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres, no-mocks verification of RentLedgerApplicationService.
 * Unlike RentLedgerApplicationServiceTest (Mockito), this suite persists a
 * real Lease and exercises the actual DB constraints from V32/V33 — in
 * particular the uk_rent_ledger_entries_lease_period and
 * uk_rent_transactions_tenant_external_reference indexes, which a mocked
 * repository cannot verify at all.
 *
 * Domain events are NOT asserted here: DomainEventPublisher's real
 * implementation/wiring hasn't been reviewed, so this suite verifies
 * outcomes via persisted state rather than guessing at an event-capture
 * mechanism. Revisit if/when a test listener for DomainEventPublisher is
 * available.
 */
class RentLedgerApplicationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RentLedgerApplicationService service;
    @Autowired
    private LeaseRepository leaseRepository;
    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Autowired
    private RentTransactionRepository rentTransactionRepository;
    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private Lease persistedLease;

    /**
     * Full-month lease: starts on the 1st, so postCharge's proration branch
     * (billingPeriodStart.equals(lease.getStartDate()) && dayOfMonth() > 1)
     * never triggers for the opening period — deliberately kept simple here;
     * the mid-month/prorated case is covered in its own nested class below
     * with a dedicated lease.
     */
    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        Lease lease = Lease.create(
                tenantId,
                UUID.randomUUID(),        // propertyId
                UUID.randomUUID(),        // unitId
                UUID.randomUUID(),        // tenantProfileId
                "LSE-IT-" + UUID.randomUUID(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.of(2026, 6, 1), // startDate — 1st of month, no proration
                LocalDate.of(2027, 6, 1), // endDate
                new BigDecimal("1000.00"),// monthlyRent
                new BigDecimal("1000.00"),// securityDeposit
                BigDecimal.ZERO,          // lateFeeAmount
                5,                        // gracePeriodDays
                false                     // autoRenew
        );

        persistedLease = leaseRepository.save(lease);
        entityManager.flush();
    }

    @Nested
    class PostCharge {

        @Test
        @Transactional
        void createsEntryAndOpeningChargeAtomically() {
            RentLedgerEntry entry = service.postCharge(
                    tenantId, "corr-1", persistedLease.getId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
            );
            entityManager.flush();

            assertThat(entry.getAmountDue()).isEqualByComparingTo("1000.00");
            assertThat(entry.isProrated()).isFalse();

            var persistedTx = rentTransactionRepository.findByLedgerEntry(tenantId, entry.getId());
            assertThat(persistedTx).hasSize(1);
            assertThat(persistedTx.get(0).getType()).isEqualTo(RentTransactionType.RENT_CHARGE);
            assertThat(persistedTx.get(0).getAmount()).isEqualByComparingTo("1000.00");
            assertThat(persistedTx.get(0).getSource()).isEqualTo(RentTransactionSource.SYSTEM);
        }

        @Test
        @Transactional
        void secondCallForSamePeriodIsIdempotentAgainstRealConstraint() {
            RentLedgerEntry first = service.postCharge(
                    tenantId, "corr-1", persistedLease.getId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
            );
            entityManager.flush();

            RentLedgerEntry second = service.postCharge(
                    tenantId, "corr-2", persistedLease.getId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
            );

            assertThat(second.getId()).isEqualTo(first.getId());
            assertThat(rentLedgerEntryRepository.findByLease(tenantId, persistedLease.getId())).hasSize(1);
            assertThat(rentTransactionRepository.findByLease(tenantId, persistedLease.getId())).hasSize(1);
        }

        @Test
        @Transactional
        void throwsLeaseNotFoundForRealMissingLease() {
            UUID missingLeaseId = UUID.randomUUID();

            assertThatThrownBy(() -> service.postCharge(
                    tenantId, "corr-1", missingLeaseId,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
            )).isInstanceOf(RentLedgerStateException.class);
        }
    }

    @Nested
    class ProratedOpeningPeriod {

        private UUID midMonthTenantId;
        private Lease midMonthLease;

        @BeforeEach
        void setUpMidMonthLease() {
            midMonthTenantId = UUID.randomUUID();

            Lease lease = Lease.create(
                    midMonthTenantId,
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "LSE-IT-MIDMONTH-" + UUID.randomUUID(),
                    LeaseType.FIXED_TERM,
                    BillingCycle.MONTHLY,
                    LocalDate.of(2026, 3, 15), // starts mid-month, 31-day month
                    LocalDate.of(2027, 3, 15),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1000.00"),
                    BigDecimal.ZERO,
                    5,
                    false
            );
            midMonthLease = leaseRepository.save(lease);
            entityManager.flush();
        }

        @Test
        @Transactional
        void proratesAgainstRealCalendarMonth() {
            RentLedgerEntry entry = service.postCharge(
                    midMonthTenantId, "corr-1", midMonthLease.getId(),
                    LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 3, 15)
            );
            entityManager.flush();

            // 17 occupied days (15th-31st inclusive) of 31 -> 1000 * 17/31 = 548.39
            assertThat(entry.isProrated()).isTrue();
            assertThat(entry.getAmountDue()).isEqualByComparingTo("548.39");
        }
    }

    @Nested
    class ApplyTransaction {

        private RentLedgerEntry entry;

        @BeforeEach
        void postOpeningCharge() {
            entry = service.postCharge(
                    tenantId, "corr-1", persistedLease.getId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
            );
            entityManager.flush();
        }

        @Test
        @Transactional
        void appliesPaymentAndPersistsUpdatedBalance() {
            RentLedgerEntry updated = service.applyTransaction(
                    tenantId, "corr-2", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-IT-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );
            entityManager.flush();
            entityManager.clear();

            RentLedgerEntry reloaded = rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId)
                    .orElseThrow();
            assertThat(reloaded.getAmountPaid()).isEqualByComparingTo("400.00");
            assertThat(updated.getVersion()).isNotNull();
        }

        @Test
        @Transactional
        void duplicateExternalReferenceIsAbsorbedAgainstRealUniqueIndex() {
            service.applyTransaction(
                    tenantId, "corr-2", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-IT-DUPLICATE", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );
            entityManager.flush();

            // Second call with the SAME externalReference — must be absorbed as a no-op,
            // not throw, exercising the real uk_rent_transactions_tenant_external_reference
            // partial unique index from V33, not a mocked exception.
            RentLedgerEntry result = service.applyTransaction(
                    tenantId, "corr-3", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-IT-DUPLICATE", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            assertThat(result.getAmountPaid()).isEqualByComparingTo("400.00"); // only ONE payment landed
            assertThat(rentTransactionRepository.findByLedgerEntry(tenantId, entry.getId())).hasSize(2);
            // 2 = the original RENT_CHARGE + the single successful PAYMENT
        }

        @Test
        @Transactional
        void multipleCashPaymentsWithNullExternalReferenceDoNotCollide() {
            service.applyTransaction(
                    tenantId, "corr-2", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("100.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
            );
            entityManager.flush();

            RentLedgerEntry result = service.applyTransaction(
                    tenantId, "corr-3", entry.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("100.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
            );

            assertThat(result.getAmountPaid()).isEqualByComparingTo("200.00");
        }
    }
}