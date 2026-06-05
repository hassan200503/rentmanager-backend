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

        if (lease.getStatus() != LeaseStatus.DRAFT
                && lease.getStatus() != LeaseStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Lease not in activatable state");
        }
    }

    public void validateTermination(Lease lease) {

        if (!lease.isActive()) {
            throw new IllegalStateException("Only active leases can be terminated");
        }
    }

    public void validateRenewal(Lease lease) {

        if (!lease.isActive() && !lease.isExpired()) {
            throw new IllegalStateException("Invalid renewal state");
        }
    }

    public void validateUnitAvailability(UUID unitId, boolean hasActiveLease) {

        if (hasActiveLease) {
            throw new IllegalStateException("Unit already occupied");
        }
    }
}