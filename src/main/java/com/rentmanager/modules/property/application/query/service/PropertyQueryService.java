package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PropertyQueryService {

    PropertyResponse getById(UUID tenantId, UUID propertyId);

    Page<PropertyResponse> getAll(UUID tenantId, Pageable pageable);

    Page<PropertyResponse> search(UUID tenantId, String keyword, Pageable pageable);

    List<PropertyResponse> getByOwner(UUID tenantId, UUID ownerId);

    List<PropertyResponse> getByStatus(UUID tenantId, String status);
}