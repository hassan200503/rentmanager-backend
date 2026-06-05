package com.rentmanager.modules.property.application.query.handler;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyQueryHandler {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    /**
     * Get single property (read side)
     */
    public PropertyResponse handleGetById(UUID tenantId,
                                          UUID propertyId) {

        return propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .map(propertyMapper::toResponse)
                .orElseThrow(() ->
                        new IllegalArgumentException("Property not found"));
    }

    /**
     * Get paginated properties (read side)
     */
    public List<PropertyResponse> handleGetAll(UUID tenantId,
                                               int page,
                                               int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<com.rentmanager.modules.property.domain.model.Property> result =
                propertyRepository.findAllByTenantId(tenantId, pageable);

        return result
                .stream()
                .map(propertyMapper::toResponse)
                .toList();
    }
}