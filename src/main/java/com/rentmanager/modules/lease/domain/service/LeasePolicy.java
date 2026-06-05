package com.rentmanager.modules.lease.domain.service;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * PURE RULE ENGINE
 * - No repository access
 * - No workflow orchestration
 * - No duplication of LeaseDomainService logic
 *
 * Only reusable invariants that may be shared across:
 * - domain service
 * - application service
 * - event handlers (future)
 */
@Component
public class LeasePolicy {

    /**
     * Generic state check helper (reusable guard)
     */
    public void requireStatus(Lease lease, LeaseStatus expected) {

        if (lease.getStatus() != expected) {
            throw new IllegalStateException(
                    "Invalid lease state. Expected: " + expected
            );
        }
    }

    /**
     * Ensures lease is not already closed
     */
    public void requireNotClosed(Lease lease) {

        if (lease.isTerminated()
                || lease.getStatus() == LeaseStatus.TERMINATED) {

            throw new IllegalStateException(
                    "Lease is already closed"
            );
        }
    }

    /**
     * Ensures lease has valid active lifecycle state
     */
    public void requireActiveOrExpired(Lease lease) {

        if (!lease.isActive() && !lease.isExpired()) {
            throw new IllegalStateException(
                    "Lease must be active or expired"
            );
        }
    }

    /**
     * Generic tenant boundary validation (pure check only)
     */
    public void requireSameTenant(UUID tenantId, UUID targetTenantId) {

        if (!tenantId.equals(targetTenantId)) {
            throw new IllegalStateException(
                    "Tenant isolation violation"
            );
        }
    }
}