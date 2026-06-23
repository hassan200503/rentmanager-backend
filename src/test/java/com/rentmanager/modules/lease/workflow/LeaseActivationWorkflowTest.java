package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LeaseActivationWorkflowTest {

    private Lease createValidLease() {
        return Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-2001",
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

    @Test
    void shouldActivateApprovedLease() {

        Lease lease = createValidLease();

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        assertTrue(lease.isActive());
        assertEquals(LeaseStatus.ACTIVE, lease.getStatus());
        assertNotNull(lease.getStartDate());
    }

    @Test
    void shouldRejectActivationWhenLeaseIsDraft() {

        Lease lease = createValidLease();

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::activate
        );

        assertEquals(ErrorCode.LEASE_ACTIVATION_ONLY_AWAITING_DEPOSIT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldRejectReactivationOfActiveLease() {

        Lease lease = createValidLease();

        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::activate
        );

        assertEquals(ErrorCode.LEASE_ACTIVATION_ONLY_AWAITING_DEPOSIT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldRejectActivationAfterTermination() {

        Lease lease = createValidLease();

        lease.reject("invalid contract");

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::activate
        );

        assertEquals(ErrorCode.LEASE_ACTIVATION_ONLY_AWAITING_DEPOSIT_ALLOWED, ex.getErrorCode());
    }
}