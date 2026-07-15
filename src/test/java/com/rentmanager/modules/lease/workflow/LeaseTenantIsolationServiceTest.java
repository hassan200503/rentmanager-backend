package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowValidator;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.LeaseDomainService;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import com.rentmanager.shared.exception.LeaseStateException;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaseTenantIsolationServiceTest {

    // =========================
    // FACTORY
    // =========================
    private Lease createLease(UUID tenantId) {
        return Lease.create(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-" + System.nanoTime(),
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(12),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );
    }

    private LeaseWorkflowEngine engine() {
        return new LeaseWorkflowEngine(
                new LeaseWorkflowValidator(),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );
    }

    // =========================
    // TEST 1: TENANT ISOLATION IN STATE MUTATION
    // =========================
    @Test
    void shouldPreventCrossTenantWorkflowMutation() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA = createLease(tenantA);
        leaseA.approve();
        leaseA.markAwaitingDeposit();
        leaseA.activate();

        Lease leaseB = createLease(tenantB);
        leaseB.approve();
        leaseB.markAwaitingDeposit();
        leaseB.activate();

        assertNotEquals(leaseA.getTenantId(), leaseB.getTenantId());
        assertTrue(leaseA.isActive());
        assertTrue(leaseB.isActive());
    }

    // =========================
    // TEST 2: CROSS-TENANT ACCESS CONTROL SIMULATION
    // =========================
    @Test
    void shouldPreventCrossTenantTerminationWithoutActiveState() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA = createLease(tenantA);
        leaseA.approve();
        leaseA.markAwaitingDeposit();
        leaseA.activate();

        Lease leaseB = createLease(tenantB);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> leaseB.terminate(
                        TerminationType.TENANT_REQUEST,
                        "invalid access",
                        "SYSTEM",
                        leaseB.getTenantId()
                )
        );

        assertEquals(
                ErrorCode.LEASE_TERMINATION_ONLY_ACTIVE_ALLOWED,
                ex.getErrorCode()
        );
    }

    // =========================
    // TEST 3: TENANT SCOPE SAFETY ON ACTIVE LEASE
    // =========================
    @Test
    void shouldAllowTerminationOnlyWithinCorrectLifecycle() {

        UUID tenantId = UUID.randomUUID();

        Lease lease = createLease(tenantId);

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        assertDoesNotThrow(() ->
                lease.terminate(
                        TerminationType.TENANT_REQUEST,
                        "valid request",
                        "SYSTEM",
                        lease.getTenantId()
                )
        );

        assertEquals(LeaseStatus.TERMINATED, lease.getStatus());
    }
}