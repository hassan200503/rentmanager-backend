package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PropertyTypeDescriptor;
import com.rentmanager.modules.property.application.dto.response.PropertyTypeMetadataResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.shared.exception.PropertyNotFoundException;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyQueryServiceImpl implements PropertyQueryService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    // =========================================================
    // GET BY ID (TENANT SAFE - CORE RULE)
    // =========================================================
    @Override
    public PropertyResponse getById(UUID tenantId, UUID propertyId) {

        return propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .map(propertyMapper::toResponse)
                .orElseThrow(() ->
                        new PropertyNotFoundException(
                                propertyId,
                                "Property not found for tenant"
                        )
                );
    }

    // =========================================================
    // GET ALL (TENANT SCOPED)
    // =========================================================
    @Override
    public Page<PropertyResponse> getAll(UUID tenantId, Pageable pageable) {

        return propertyRepository.findAllByTenantId(tenantId, pageable)
                .map(propertyMapper::toResponse);
    }

    // =========================================================
    // SEARCH (TENANT SCOPED)
    // =========================================================
    @Override
    public Page<PropertyResponse> search(UUID tenantId, String keyword, Pageable pageable) {

        return propertyRepository.searchByTenantId(tenantId, keyword, pageable)
                .map(propertyMapper::toResponse);
    }

    // =========================================================
    // BY OWNER (TENANT SCOPED)
    // =========================================================
    @Override
    public List<PropertyResponse> getByOwner(UUID tenantId, UUID ownerId) {

        return propertyRepository.findByOwnerIdAndTenantId(ownerId, tenantId)
                .stream()
                .map(propertyMapper::toResponse)
                .toList();
    }

    // =========================================================
    // BY STATUS (TENANT SCOPED)
    // =========================================================
    @Override
    public List<PropertyResponse> getByStatus(UUID tenantId, String status) {

        return propertyRepository.findByStatusAndTenantId(status, tenantId)
                .stream()
                .map(propertyMapper::toResponse)
                .toList();
    }

    // =========================================================
    // TAXONOMY METADATA (single source of truth for the form)
    // =========================================================
    /**
     * Derives each selectable property type once, on the backend, so client
     * code never re-implements the rule. derivedPremisesType is never
     * MIXED_USE. The premises list is supplied in a stable order for the
     * override selector (MIXED_USE only reachable through an explicit
     * override).
     */
    @Override
    public PropertyTypeMetadataResponse getPropertyTypes() {

        List<PropertyTypeDescriptor> descriptors = Arrays.stream(PropertyType.values())
                .map(type -> new PropertyTypeDescriptor(type, PremisesType.fromPropertyType(type)))
                .toList();

        return PropertyTypeMetadataResponse.builder()
                .propertyTypes(descriptors)
                .premisesTypes(List.of(PremisesType.RESIDENTIAL, PremisesType.COMMERCIAL, PremisesType.MIXED_USE))
                .build();
    }
}