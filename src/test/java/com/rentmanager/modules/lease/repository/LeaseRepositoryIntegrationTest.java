package com.rentmanager.modules.lease.repository;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class LeaseRepositoryIntegrationTest {

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID propertyId;
    private UUID unitId;
    private UUID tenantProfileId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        tenantProfileId = UUID.randomUUID();
    }

    private Lease createLease() {
        return Lease.create(
                tenantId,
                propertyId,
                unitId,
                tenantProfileId,
                uniqueLeaseNumber(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                null,
                0,
                false
        );
    }

    @Test
    void shouldSaveAndRetrieveLeaseById() {
        Lease saved = leaseRepository.save(createLease());

        assertNotNull(saved);

        Lease found = leaseRepository.findById(saved.getId()).orElse(null);

        assertNotNull(found);
        assertEquals(saved.getPropertyId(), found.getPropertyId());
        assertEquals(saved.getUnitId(), found.getUnitId());
        assertEquals(saved.getTenantProfileId(), found.getTenantProfileId());
        assertEquals(saved.getRentAmount(), found.getRentAmount());
    }

    @Test
    void shouldIsolateLeasesByTenant() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA = Lease.create(
                tenantA, propertyId, unitId, tenantProfileId,
                uniqueLeaseNumber(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                new BigDecimal("1000"), new BigDecimal("2000"),
                null, 0, false
        );

        Lease leaseB = Lease.create(
                tenantB, propertyId, unitId, tenantProfileId,
                uniqueLeaseNumber(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                new BigDecimal("1000"), new BigDecimal("2000"),
                null, 0, false
        );

        leaseRepository.save(leaseA);
        leaseRepository.save(leaseB);

        entityManager.flush();

        var resultA = leaseRepository.findAllByTenant(tenantA);
        var resultB = leaseRepository.findAllByTenant(tenantB);

        assertEquals(1, resultA.size());
        assertEquals(leaseA.getLeaseNumber(), resultA.get(0).getLeaseNumber());

        assertEquals(1, resultB.size());
        assertEquals(leaseB.getLeaseNumber(), resultB.get(0).getLeaseNumber());
    }

    @Test
    void shouldFindByUnitIdAndStatus() {

        Lease lease = createLease();
        Lease saved = leaseRepository.save(lease);

        var result = leaseRepository.findByUnitIdAndStatus(
                unitId,
                LeaseStatus.DRAFT
        );

        assertTrue(result.isPresent());
        assertEquals(saved.getUnitId(), result.get().getUnitId());
    }

    @Test
    void shouldDeleteLease() {

        Lease saved = leaseRepository.save(createLease());

        // ✅ FIXED: replaced deleteById (not supported by domain repository)
        leaseRepository.delete(saved.getId());
        var found = leaseRepository.findById(saved.getId());

        assertTrue(found.isEmpty());
    }

    @Test
    void shouldNeverLeakLeasesAcrossTenantsEvenInMixedDataSet() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA1 = Lease.create(
                tenantA, propertyId, unitId, tenantProfileId,
                uniqueLeaseNumber(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                new BigDecimal("1000"), new BigDecimal("2000"),
                null, 0, false
        );

        Lease leaseA2 = Lease.create(
                tenantA, propertyId, unitId, tenantProfileId,
                uniqueLeaseNumber(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                new BigDecimal("1100"), new BigDecimal("2100"),
                null, 0, false
        );

        Lease leaseB1 = Lease.create(
                tenantB, propertyId, unitId, tenantProfileId,
                uniqueLeaseNumber(), LeaseType.FIXED_TERM, BillingCycle.MONTHLY,
                LocalDate.now(), LocalDate.now().plusMonths(1),
                new BigDecimal("1200"), new BigDecimal("2200"),
                null, 0, false
        );

        leaseRepository.save(leaseA1);
        leaseRepository.save(leaseA2);
        leaseRepository.save(leaseB1);

        entityManager.flush();

        var resultA = leaseRepository.findAllByTenant(tenantA);

        assertEquals(2, resultA.size());
        assertTrue(resultA.stream().allMatch(l -> l.getTenantId().equals(tenantA)));
        assertFalse(resultA.stream().anyMatch(l -> l.getTenantId().equals(tenantB)));
    }

    @Test
    void shouldRespectAllLeaseStatusesInQueries() {

        Lease lease = createLease();
        Lease saved = leaseRepository.save(lease);

        var draftResult = leaseRepository.findByUnitIdAndStatus(
                saved.getUnitId(),
                LeaseStatus.DRAFT
        );

        assertTrue(draftResult.isPresent());
        assertEquals(LeaseStatus.DRAFT, draftResult.get().getStatus());

        var wrongStatus = leaseRepository.findByUnitIdAndStatus(
                saved.getUnitId(),
                LeaseStatus.ACTIVE
        );

        assertTrue(wrongStatus.isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenTenantHasNoLeases() {

        UUID randomTenant = UUID.randomUUID();

        var result = leaseRepository.findAllByTenant(randomTenant);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPersistLeaseWithCorrectInitialState() {

        Lease lease = leaseRepository.save(createLease());

        Lease reloaded = leaseRepository.findById(lease.getId()).orElseThrow();

        assertEquals(LeaseStatus.DRAFT, reloaded.getStatus());
        assertEquals(lease.getLeaseNumber(), reloaded.getLeaseNumber());
        assertEquals(lease.getLeaseType(), reloaded.getLeaseType());
        assertEquals(lease.getBillingCycle(), reloaded.getBillingCycle());
        assertEquals(lease.getRentAmount(), reloaded.getRentAmount());
        assertEquals(lease.getSecurityDeposit(), reloaded.getSecurityDeposit());
    }

    @Test
    void shouldPersistLeaseStatusChanges() {

        Lease lease = leaseRepository.save(createLease());

        lease.approve();

        leaseRepository.save(lease);

        entityManager.flush();
        entityManager.clear();

        Lease reloaded = leaseRepository.findById(lease.getId()).orElseThrow();

        assertEquals(LeaseStatus.PENDING_APPROVAL, reloaded.getStatus());
    }

    @Test
    void shouldReturnOnlyActiveLeases() {

        UUID tenantId = UUID.randomUUID();

        Lease activeLease = Lease.create(
                tenantId,
                propertyId,
                unitId,
                tenantProfileId,
                uniqueLeaseNumber(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                null,
                0,
                false
        );

        activeLease.approve();
        activeLease.activate();

        Lease draftLease = createLease();

        Lease terminatedLease = Lease.create(
                tenantId,
                propertyId,
                unitId,
                tenantProfileId,
                uniqueLeaseNumber(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                new BigDecimal("1300"),
                new BigDecimal("2300"),
                null,
                0,
                false
        );

        terminatedLease.approve();
        terminatedLease.activate();
        terminatedLease.terminate(
                TerminationType.TENANT_REQUEST,
                "TEST",
                "SYSTEM",
                terminatedLease.getTenantId()
        );

        leaseRepository.save(activeLease);
        leaseRepository.save(draftLease);
        leaseRepository.save(terminatedLease);

        entityManager.flush();
        entityManager.clear();

        List<Lease> result = leaseRepository.findActiveByTenant(tenantId);

        assertEquals(1, result.size());

        Lease found = result.get(0);

        assertEquals(LeaseStatus.ACTIVE, found.getStatus());
        assertEquals(activeLease.getLeaseNumber(), found.getLeaseNumber());
    }

    public static String uniqueLeaseNumber() {
        return "LS-" + UUID.randomUUID().toString().replace("-", "");
    }
}