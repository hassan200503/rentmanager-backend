package com.rentmanager.modules.tenant.renter.domain.repository;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;

import java.util.*;
import java.util.UUID;

public interface TenantProfileRepository {

    TenantProfile save(TenantProfile profile);

    Optional<TenantProfile> findById(UUID id);

    Optional<TenantProfile> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId);

    boolean existsByClerkUserId(String clerkUserId);

    boolean existsById(UUID id);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    /**
     * Compensating action only — deletes a TenantProfile created during a
     * fulfillment saga that subsequently failed. Never called on profiles
     * reused from a prior successful reservation.
     */
    void deleteById(UUID id);

    List<TenantProfile> findAllById(Collection<UUID> ids);
}