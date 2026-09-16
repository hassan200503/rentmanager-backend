package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PropertyTypeMetadataResponse;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PropertyQueryService {

    PropertyResponse getById(UUID tenantId, UUID propertyId);

    Page<PropertyResponse> getAll(UUID tenantId, Pageable pageable);

    /**
     * Keyword, status and propertyType are each optional (null/blank means
     * no filter on that field) and independently combinable — backs the
     * dashboard Properties page's search box, status filter and type filter
     * as one paginated call instead of three incompatible endpoints.
     */
    Page<PropertyResponse> search(
            UUID tenantId,
            String keyword,
            PropertyStatus status,
            PropertyType propertyType,
            Pageable pageable
    );

    List<PropertyResponse> getByOwner(UUID tenantId, UUID ownerId);

    List<PropertyResponse> getByStatus(UUID tenantId, String status);

    PropertyTypeMetadataResponse getPropertyTypes();
}