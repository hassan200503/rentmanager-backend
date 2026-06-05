package com.rentmanager.modules.lease.application;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
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
class LeaseFullWorkflowEventTest {

    @Autowired
    private LeaseWorkflowEngine workflowEngine;

    @Autowired
    private LeaseRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldExecuteFullLeaseLifecycleWithEventConsistency() {

        UUID tenantId = UUID.randomUUID();

        Lease lease = Lease.create(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-FULL-" + UUID.randomUUID(),
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(6),
                new BigDecimal("12000"),
                new BigDecimal("25000"),
                new BigDecimal("800"),
                7,
                false
        );

        // =====================================================
        // APPROVE
        // =====================================================

        workflowEngine.approve(lease);

        assertEquals(
                LeaseStatus.PENDING_APPROVAL,
                lease.getStatus()
        );

        // =====================================================
        // ACTIVATE
        // =====================================================

        workflowEngine.activate(lease);

        assertEquals(
                LeaseStatus.ACTIVE,
                lease.getStatus()
        );

        // =====================================================
        // PERSIST ACTIVE STATE
        // =====================================================

        Lease saved = repository.save(lease);

        assertNotNull(saved.getId());

        entityManager.flush();
        entityManager.clear();

        Lease activeReloaded = repository.findById(saved.getId())
                .orElseThrow();

        assertEquals(
                LeaseStatus.ACTIVE,
                activeReloaded.getStatus()
        );

        // =====================================================
        // TERMINATE
        // =====================================================

        workflowEngine.terminate(
                activeReloaded,
                TerminationType.TENANT_REQUEST,
                "End of lease"
        );

        assertEquals(
                LeaseStatus.TERMINATED,
                activeReloaded.getStatus()
        );

        // =====================================================
        // PERSIST TERMINATED STATE
        // =====================================================

        repository.save(activeReloaded);

        entityManager.flush();
        entityManager.clear();

        // =====================================================
        // VERIFY FINAL DATABASE STATE
        // =====================================================

        Lease terminatedReloaded =
                repository.findById(saved.getId())
                        .orElseThrow();

        assertEquals(
                LeaseStatus.TERMINATED,
                terminatedReloaded.getStatus()
        );

        assertTrue(
                terminatedReloaded.isTerminated()
        );

        assertEquals(
                tenantId,
                terminatedReloaded.getTenantId()
        );
    }
}