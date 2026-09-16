package com.rentmanager.crossmodule.transaction_safety;


import com.rentmanager.crossmodule.support.TransactionBoundary;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;

import com.rentmanager.modules.unit.domain.model.Unit;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// Real Postgres from the shared Testcontainers bridge — not CrossModuleBaseIT,
// whose class-level @Transactional would wrap the very rollback under test.
// Bare @SpringBootTest reached localhost:5432: present locally, absent in CI.
@SpringBootTest
@ContextConfiguration(initializers = PostgresSpringBridge.class)
class CrossModuleRollbackIT {

    @Autowired
    private TransactionBoundary transactionBoundary;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void should_rollback_entire_cross_module_transaction_on_failure() {

        UUID tenantId = UUID.randomUUID();

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                transactionBoundary.execute(() -> {

                    // =====================================
                    // PROPERTY
                    // =====================================

                    Property property = Property.create(
                            tenantId,
                            "Rollback Tower",
                            PropertyType.BEDSITTER,
                            null,
                            null,
                            null,
                            "desc",
                            "CORR"
                    );

                    property.activate("TX-1");

                    entityManager.persist(property);
                    entityManager.flush();

                    // =====================================
                    // UNIT
                    // =====================================

                    Unit unit = Unit.create(
                            tenantId,
                            property.getId(),
                            "U-ROLLBACK",
                            "Rollback Unit",
                            null,
                            new BigDecimal("1500"),
                            null,
                            "desc",
                            "TX-1"
                    );

                    entityManager.persist(unit);
                    entityManager.flush();

                    // =====================================
                    // LEASE
                    // =====================================

                    Lease lease = Lease.create(
                            tenantId,
                            property.getId(),
                            unit.getId(),
                            UUID.randomUUID(),
                            "LEASE-ROLLBACK",
                            LeaseType.FIXED_TERM,
                            BillingCycle.MONTHLY,
                            LocalDate.now(),
                            LocalDate.now().plusMonths(12),
                            new BigDecimal("1500"),
                            new BigDecimal("300"),
                            new BigDecimal("50"),
                            5,
                            false
                    );

                    entityManager.persist(lease);
                    entityManager.flush();

                    // Force rollback
                    throw new IllegalStateException("Simulated downstream failure");
                })
        );

        // =====================================
        // ASSERT WRAPPED EXCEPTION
        // =====================================

        assertNotNull(ex);
        assertTrue(
                ex.getMessage().toLowerCase().contains("transaction"),
                "TransactionBoundary should wrap failures"
        );

        // =====================================
        // CLEAR PERSISTENCE CONTEXT
        // =====================================

        entityManager.clear();

        // =====================================
        // VERIFY DATABASE STATE
        // =====================================
        // Counted in SQL against the tables: the domain classes are not JPA
        // entities, so a JPQL "from Property" never resolved.

        Long propertyCount = ((Number) entityManager.createNativeQuery(
                        "select count(*) from properties where tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();

        Long unitCount = ((Number) entityManager.createNativeQuery(
                        "select count(*) from units where tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();

        Long leaseCount = ((Number) entityManager.createNativeQuery(
                        "select count(*) from leases where tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();

        // =====================================
        // ASSERT FULL ROLLBACK
        // =====================================

        assertEquals(0L, propertyCount,
                "Property must be rolled back completely");

        assertEquals(0L, unitCount,
                "Unit must be rolled back completely");

        assertEquals(0L, leaseCount,
                "Lease must be rolled back completely");
    }
}