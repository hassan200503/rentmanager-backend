package com.rentmanager.modules.lease.repository;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LeaseTenantIsolationRepositoryTest {

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldIsolateLeasesStrictlyPerTenant() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA1 = createLease(tenantA, "LS-A1");
        Lease leaseA2 = createLease(tenantA, "LS-A2");
        Lease leaseB1 = createLease(tenantB, "LS-B1");

        leaseRepository.save(leaseA1);
        leaseRepository.save(leaseA2);
        leaseRepository.save(leaseB1);

        entityManager.flush();
        entityManager.clear();

        var resultA = leaseRepository.findAllByTenant(tenantA);
        var resultB = leaseRepository.findAllByTenant(tenantB);

        assertEquals(2, resultA.size());
        assertEquals(1, resultB.size());

        assertTrue(resultA.stream().allMatch(l -> l.getTenantId().equals(tenantA)));
        assertTrue(resultB.stream().allMatch(l -> l.getTenantId().equals(tenantB)));

        assertFalse(
                resultA.stream().anyMatch(l -> l.getTenantId().equals(tenantB))
        );
    }

    @Test
    void shouldNeverLeakActiveLeasesAcrossTenants() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease activeA = createLease(tenantA, "LS-A1");
        Lease activeB = createLease(tenantB, "LS-B1");

        activeA.approve();
        activeA.markAwaitingDeposit();
        activeA.activate();

        activeB.approve();
        activeB.markAwaitingDeposit();
        activeB.activate();

        leaseRepository.save(activeA);
        leaseRepository.save(activeB);

        entityManager.flush();
        entityManager.clear();

        var activeForA = leaseRepository.findActiveByTenant(tenantA);

        assertEquals(1, activeForA.size());
        assertEquals(LeaseStatus.ACTIVE, activeForA.get(0).getStatus());

        assertTrue(
                activeForA.stream().noneMatch(l -> l.getTenantId().equals(tenantB))
        );
    }

    @Test
    void shouldPreventCrossTenantUnitConflicts() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        UUID sharedUnit = UUID.randomUUID();

        Lease leaseA = createLease(tenantA, "LS-A1", sharedUnit);
        Lease leaseB = createLease(tenantB, "LS-B1", sharedUnit);

        leaseA.approve();
        leaseA.markAwaitingDeposit();
        leaseA.activate();

        leaseB.approve();
        leaseB.markAwaitingDeposit();
        leaseB.activate();

        leaseRepository.save(leaseA);
        leaseRepository.save(leaseB);

        entityManager.flush();
        entityManager.clear();

        var activeA = leaseRepository.findActiveByTenant(tenantA);
        var activeB = leaseRepository.findActiveByTenant(tenantB);

        assertEquals(1, activeA.size());
        assertEquals(1, activeB.size());

        assertNotEquals(
                activeA.get(0).getId(),
                activeB.get(0).getId()
        );
    }

    private Lease createLease(UUID tenantId, String leaseNumber) {
        return createLease(tenantId, leaseNumber, UUID.randomUUID());
    }

    private Lease createLease(UUID tenantId, String leaseNumber, UUID unitId) {

        return Lease.create(
                tenantId,
                UUID.randomUUID(),
                unitId,
                UUID.randomUUID(),
                leaseNumber,
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(1),
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                new BigDecimal("100"),
                7,
                false
        );
    }
}