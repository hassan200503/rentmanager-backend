package com.rentmanager.modules.lease.domain.workflow;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SaaS-grade Lease workflow validator
 *
 * Responsibilities:
 * - enforce pre-state transition rules
 * - ensure domain invariants before workflow execution
 */
@Component
public class LeaseWorkflowValidator {

    public void validateActivation(Lease lease) {

        BigDecimal rent = lease.getRentAmount();

        if (rent == null || rent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Invalid rent: must be greater than 0");
        }

        if (lease.getStatus() != LeaseStatus.AWAITING_DEPOSIT) {
            throw new IllegalStateException("Lease not in activatable state");
        }
    }

    public void validateTermination(Lease lease) {

        LeaseStatus status = lease.getStatus();
        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.RENEWED) {
            throw new IllegalStateException("Only active or renewed leases can be terminated");
        }
    }

    public void validateRenewal(Lease lease) {

        LeaseStatus status = lease.getStatus();
        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.EXPIRED && status != LeaseStatus.RENEWED) {
            throw new IllegalStateException("Invalid renewal state");
        }
    }

    // NEW: mirrors Lease.expire()'s own guard, defense-in-depth, matching
    // the pattern every other engine-level transition already follows
    // (e.g. validateActivation duplicates Lease.activate()'s own check).
    public void validateExpiry(Lease lease) {

        LeaseStatus status = lease.getStatus();
        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.RENEWED) {
            throw new IllegalStateException("Only active or renewed leases can expire");
        }
    }

    // NEW: mirrors Lease.cancel()'s own guard.
    public void validateCancellation(Lease lease) {

        if (lease.getStatus() == LeaseStatus.ACTIVE) {
            throw new IllegalStateException("Cannot cancel an active lease");
        }
    }

    public void validateUnitAvailability(UUID unitId, boolean hasActiveLease) {

        if (hasActiveLease) {
            throw new IllegalStateException("Unit already occupied");
        }
    }
}