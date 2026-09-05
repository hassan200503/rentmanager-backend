package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-Postgres verification of {@code findAllByTenantAndIdIn} — added to
 * fix a real defect (2026-09-03): the tenant portal's payment history mapped
 * every transaction's Status column to its own transaction type
 * ("Rent Charge" instead of PAID/OVERDUE/DUE) because it never looked up the
 * linked {@code RentLedgerEntry}, which is where that status actually lives.
 * This is the bulk lookup that fix depends on — a Spring Data derived query
 * (two field names it hadn't been asked to combine before), verified here
 * against a real table rather than trusted on the name alone.
 */
@Transactional
class RentLedgerEntryBulkLookupIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Autowired
    private LeaseRepository leaseRepository;
    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID leaseId;
    private UUID unitId;
    private UUID tenantProfileId;

    @BeforeEach
    void setUp() {
        MinimalTenantChainFixture.Chain chain = MinimalTenantChainFixture.persistFullChain(entityManager);
        tenantId = chain.tenantId();
        unitId = chain.unitId();
        tenantProfileId = chain.tenantProfileId();
        leaseId = persistLease(tenantId, chain.propertyId(), unitId, tenantProfileId).getId();
    }

    private Lease persistLease(UUID tenantId, UUID propertyId, UUID unitId, UUID tenantProfileId) {
        Lease lease = Lease.create(
                tenantId, propertyId, unitId, tenantProfileId,
                "LSE-IT-" + UUID.randomUUID(),
                LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1),
                new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                BigDecimal.ZERO, 5, false
        );
        Lease saved = leaseRepository.save(lease);
        entityManager.flush();
        return saved;
    }

    private RentLedgerEntry persistEntry(UUID tenantId, LocalDate periodStart) {
        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId, "corr-" + UUID.randomUUID(), leaseId, unitId, tenantProfileId,
                periodStart, periodStart.plusDays(29), periodStart,
                new BigDecimal("1000.00"), false
        );
        RentLedgerEntry saved = rentLedgerEntryRepository.save(entry);
        entityManager.flush();
        return saved;
    }

    @Test
    void returnsOnlyTheRequestedIdsForTheGivenTenant() {
        RentLedgerEntry wanted1 = persistEntry(tenantId, LocalDate.of(2026, 6, 1));
        RentLedgerEntry wanted2 = persistEntry(tenantId, LocalDate.of(2026, 7, 1));
        RentLedgerEntry notRequested = persistEntry(tenantId, LocalDate.of(2026, 8, 1));

        List<RentLedgerEntry> result = rentLedgerEntryRepository.findAllByTenantAndIdIn(
                tenantId, List.of(wanted1.getId(), wanted2.getId()));

        assertThat(result).extracting(RentLedgerEntry::getId)
                .containsExactlyInAnyOrder(wanted1.getId(), wanted2.getId())
                .doesNotContain(notRequested.getId());
    }

    @Test
    void doesNotLeakAnotherTenantsEntryEvenIfItsIdIsRequested() {
        RentLedgerEntry mine = persistEntry(tenantId, LocalDate.of(2026, 6, 1));

        MinimalTenantChainFixture.Chain otherChain = MinimalTenantChainFixture.persistFullChain(entityManager);
        UUID otherLeaseId = persistLease(
                otherChain.tenantId(), otherChain.propertyId(), otherChain.unitId(), otherChain.tenantProfileId()
        ).getId();
        RentLedgerEntry theirs = RentLedgerEntry.create(
                otherChain.tenantId(), "corr-other", otherLeaseId, otherChain.unitId(), otherChain.tenantProfileId(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1),
                new BigDecimal("1000.00"), false
        );
        RentLedgerEntry savedTheirs = rentLedgerEntryRepository.save(theirs);
        entityManager.flush();

        // Asking tenantId's repository call for BOTH ids — the other
        // tenant's row must not come back even though its id was requested.
        List<RentLedgerEntry> result = rentLedgerEntryRepository.findAllByTenantAndIdIn(
                tenantId, List.of(mine.getId(), savedTheirs.getId()));

        assertThat(result).extracting(RentLedgerEntry::getId).containsExactly(mine.getId());
    }

    @Test
    void emptyIdListReturnsEmptyRatherThanErroring() {
        assertThat(rentLedgerEntryRepository.findAllByTenantAndIdIn(tenantId, List.of())).isEmpty();
    }
}
