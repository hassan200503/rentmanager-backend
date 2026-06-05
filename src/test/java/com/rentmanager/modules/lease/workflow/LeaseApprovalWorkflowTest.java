package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LeaseApprovalWorkflowTest {

    @Test
    void shouldMoveFromDraftToPendingApproval() {

        Lease lease = createValidLease();

        assertEquals(LeaseStatus.DRAFT, lease.getStatus());

        lease.approve();

        assertEquals(LeaseStatus.PENDING_APPROVAL, lease.getStatus());
    }

    @Test
    void shouldRejectApprovalOnNonDraftLease() {

        Lease lease = createValidLease();

        lease.approve();

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::approve
        );

        assertEquals(ErrorCode.LEASE_APPROVAL_ONLY_DRAFT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldNotAllowApprovalAfterActivation() {

        Lease lease = createValidLease();

        lease.approve();
        lease.activate();

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::approve
        );

        assertEquals(ErrorCode.LEASE_APPROVAL_ONLY_DRAFT_ALLOWED, ex.getErrorCode());
    }

    @Test
    void shouldNotAllowApprovalOnTerminatedLease() {

        Lease lease = createValidLease();

        lease.reject("bad data");

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                lease::approve
        );

        assertEquals(ErrorCode.LEASE_APPROVAL_ONLY_DRAFT_ALLOWED, ex.getErrorCode());
    }

    private Lease createValidLease() {
        return Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-1001",
                LeaseType.FIXED_TERM,
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
}