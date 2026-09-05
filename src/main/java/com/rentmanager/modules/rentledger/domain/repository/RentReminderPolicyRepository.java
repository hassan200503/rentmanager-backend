package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;

import java.util.List;
import java.util.UUID;

/**
 * Port for a landlord's reminder cadence configuration.
 *
 * <p>Callers should prefer resolving through the application service rather
 * than reading this directly: a landlord with no stored rows is a normal
 * state, not an error, and must fall back to
 * {@link RentReminderPolicy#defaultFor}.
 */
public interface RentReminderPolicyRepository {

    /** Every stored milestone preference for one landlord. May be empty. */
    List<RentReminderPolicy> findByTenant(UUID tenantId);

    /**
     * Upserts a landlord's cadence, keyed on {@code (tenantId, milestone)}.
     *
     * <p>Callers pass the complete set rather than a delta. A partial save
     * would let the stored cadence and the grid the landlord was looking at
     * drift apart, and the thing being configured here decides whether real
     * people get messaged.
     */
    List<RentReminderPolicy> saveAll(UUID tenantId, List<RentReminderPolicy> policies);
}
