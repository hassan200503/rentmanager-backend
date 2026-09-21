package com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantProfileJpaRepository extends JpaRepository<TenantProfileEntity, UUID> {

    Optional<TenantProfileEntity> findByTenantIdAndClerkUserId(UUID tenantId, String clerkUserId);

    List<TenantProfileEntity> findAllByClerkUserId(String clerkUserId);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
    boolean existsByClerkUserId(String clerkUserId);

    /**
     * Every Clerk user that holds a renter identity (distinct). Used by the
     * platform-admin identity snapshot to classify renters. Renter profiles
     * are the ONLY renter-side identity rows in the system, so this set is
     * the authoritative renter membership signal.
     */
    @Query("select distinct t.clerkUserId from TenantProfileEntity t")
    List<String> findAllClerkUserIds();

    /**
     * Backs the Tenants page's search box: a landlord searching "Hassan"
     * needs every renter with that name, not just whichever page of leases
     * happens to be loaded. Landlord-scoped ({@code tenantId} here is the
     * landlord, per the naming trap — see module docs) since one renter's
     * name is only ever searched within one landlord's book.
     */
    @Query("""
            SELECT t FROM TenantProfileEntity t
            WHERE t.tenantId = :tenantId
              AND (LOWER(t.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR t.phone LIKE CONCAT('%', :keyword, '%'))
            """)
    List<TenantProfileEntity> searchByNameOrPhone(
            @Param("tenantId") UUID tenantId,
            @Param("keyword") String keyword
    );

    /**
     * One landlord's renters for the Renters page. Derived from the method
     * name on purpose — no @Query, so it cannot drift from the ordering the
     * name promises.
     */
    Page<TenantProfileEntity> findAllByTenantIdOrderByFullNameAsc(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT t FROM TenantProfileEntity t
            WHERE t.tenantId = :tenantId AND t.phone = :phone AND t.clerkUserId IS NULL
            """)
    Optional<TenantProfileEntity> findUnlinkedByTenantIdAndPhone(
            @Param("tenantId") UUID tenantId,
            @Param("phone") String phone
    );

    @Query("""
            SELECT t FROM TenantProfileEntity t
            WHERE t.clerkUserId IS NULL AND LOWER(t.email) = LOWER(:email)
            """)
    List<TenantProfileEntity> findUnlinkedByEmail(@Param("email") String email);

}