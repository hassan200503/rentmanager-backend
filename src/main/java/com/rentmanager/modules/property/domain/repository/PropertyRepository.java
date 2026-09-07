package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyRepository {

    Property save(Property property);

    Optional<Property> findById(UUID id);

    Optional<Property> findByIdAndTenantId(UUID id, UUID tenantId);

    void delete(Property property);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    Page<Property> findAllByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Bulk lookup for enriching a page of leases with their property — one
     * query per page of leases rather than one per lease row.
     */
    List<Property> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    Page<Property> search(String keyword, Pageable pageable);

    List<Property> findByOwnerId(UUID ownerId);

    List<Property> findByStatus(String status);

    Page<Property> findAll(Pageable pageable);

    default Property getByIdOrThrow(UUID id, UUID tenantId) {
        return findByIdAndTenantId(id, tenantId)
                .orElseThrow(() ->
                        new com.rentmanager.shared.exception.ResourceNotFoundException(
                                "Property not found",
                                com.rentmanager.shared.exception.ErrorCode.PROPERTY_NOT_FOUND
                        )
                );
    }

    Page<Property> search(String tenantId, String keyword, Pageable pageable);

    Page<Property> searchByTenantId(UUID tenantId, String keyword, Pageable pageable);

    List<Property> findByOwnerIdAndTenantId(UUID ownerId, UUID tenantId);

    List<Property> findByStatusAndTenantId(String status, UUID tenantId);

    // =====================================================
    // PUBLIC LISTING HARDENING (2026-07-08)
    // Status-filtered equivalents used by the public read path. Uses the
    // domain PropertyStatus enum directly (unlike the legacy findByStatus
    // above, which takes a String) since these are new methods with no
    // pre-existing String-typed callers to stay compatible with.
    // =====================================================

    Page<Property> findByStatus(PropertyStatus status, Pageable pageable);

    Page<Property> searchByStatus(String keyword, PropertyStatus status, Pageable pageable);

    Page<Property> searchByStatusAndLocation(
            String keyword,
            String location,
            PropertyStatus status,
            Pageable pageable
    );

    Optional<Property> findByIdAndStatus(UUID id, PropertyStatus status);

    /**
     * Bulk load for the public listings page, which selects ids by vacancy
     * first and then needs the properties behind them. Status is re-asserted
     * here so this method is safe to call with ids from any source.
     */
    List<Property> findAllByIdInAndStatus(List<UUID> ids, PropertyStatus status);

    /**
     * Landlord dashboard Properties page: keyword, status and propertyType
     * are each optional (null/blank means "no filter on this field") and
     * independently combinable, always scoped to the tenant. Backs a single
     * paginated endpoint so the frontend never has to choose between search
     * and status filtering.
     */
    Page<Property> searchByTenantIdWithFilters(
            UUID tenantId,
            String keyword,
            PropertyStatus status,
            PropertyType propertyType,
            Pageable pageable
    );
}