package com.rentmanager.modules.property.domain.repository;

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
}

