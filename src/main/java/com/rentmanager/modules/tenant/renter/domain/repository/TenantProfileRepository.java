package com.rentmanager.modules.tenant.renter.domain.repository;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;

import java.util.*;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantProfileRepository {

    TenantProfile save(TenantProfile profile);

    Optional<TenantProfile> findById(UUID id);

    Optional<TenantProfile> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId);

    /**
     * Every renter profile held by one person. A person renting from two
     * landlords (or who moved from one to another) has one profile per
     * landlord; a single-result lookup threw once that happened.
     */
    List<TenantProfile> findAllByClerkUserId(String clerkUserId);

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

    /**
     * Every renter under this landlord whose name or phone contains the
     * keyword. Backs the Tenants page's search box.
     */
    List<TenantProfile> searchByNameOrPhone(UUID tenantId, String keyword);

    /** One landlord's renters, newest first, for the Renters page. */
    Page<TenantProfile> findAllByTenantId(UUID tenantId, Pageable pageable);

    /**
     * An unlinked renter under this landlord with this phone. Guards against
     * entering the same tenancy twice, which would split a rent history.
     */
    Optional<TenantProfile> findUnlinkedByTenantIdAndPhone(UUID tenantId, String phone);

    /**
     * Every unlinked renter with this email, across landlords — one person may
     * rent from several. Linking only ever considers unlinked rows, so a
     * profile already claimed by an identity can never be re-pointed.
     */
    List<TenantProfile> findUnlinkedByEmail(String email);
}