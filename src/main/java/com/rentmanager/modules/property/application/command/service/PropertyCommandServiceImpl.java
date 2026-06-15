package com.rentmanager.modules.property.application.command.service;

import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyCommandServiceImpl implements PropertyCommandService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    private final CreatePropertyValidator createPropertyValidator;
    private final UpdatePropertyValidator updatePropertyValidator;
    private final ActivatePropertyValidator activatePropertyValidator;
    private final MarkFullyOccupiedValidator markFullyOccupiedValidator;
    private final PropertyMarkVacantValidator propertyMarkVacantValidator;

    // ---------------- CREATE ----------------
    @Override
    public PropertyResponse createProperty(UUID tenantId,
                                           CreatePropertyRequest request) {

        createPropertyValidator.validate(tenantId, request);

        String referenceCode = generateCorrelationId();

        Property property = Property.create(
                tenantId,
                request.getName(),
                request.getPropertyType(),
                request.getAddress(),
                request.getGeoLocation(),
                request.getDimensions(),
                request.getDescription(),
                referenceCode
        );

        Property saved = propertyRepository.save(property);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- READ ----------------
    @Override
    public PropertyResponse getProperty(UUID tenantId, UUID propertyId) {

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        return propertyMapper.toResponse(property);
    }

    // ---------------- UPDATE ----------------
    @Override
    public PropertyResponse updateProperty(UUID tenantId,
                                           UUID propertyId,
                                           UpdatePropertyRequest request) {

        updatePropertyValidator.validate(tenantId, propertyId, request);

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        property.updateDetails(request.getName(), request.getDescription());

        /*
         * FIX (critical SaaS persistence safety):
         * Ensure Hibernate is working with a managed entity state.
         * Prevent detached entity version-null crashes in edge cases.
         */
        Property managed = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        managed.updateDetails(request.getName(), request.getDescription());

        Property saved = propertyRepository.save(managed);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- ARCHIVE ----------------
    @Override
    public PropertyResponse archiveProperty(UUID tenantId,
                                            UUID propertyId) {

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        property.archive(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- ACTIVATE ----------------
    @Override
    public PropertyResponse activateProperty(UUID tenantId,
                                             UUID propertyId) {

        Property property = activatePropertyValidator.validate(tenantId, propertyId);

        property.activate(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- FULLY OCCUPIED ----------------
    @Override
    public PropertyResponse markFullyOccupied(UUID tenantId,
                                              UUID propertyId) {

        Property property = markFullyOccupiedValidator.validate(tenantId, propertyId);

        property.markFullyOccupied(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- VACANT ----------------
    @Override
    public PropertyResponse markVacant(UUID tenantId,
                                       UUID propertyId) {

        Property property = propertyMarkVacantValidator.validate(tenantId, propertyId);

        property.markVacant(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        return propertyMapper.toResponse(saved);
    }

    // ---------------- INTERNAL ----------------
    private String generateCorrelationId() {
        return "PROP-" + UUID.randomUUID();
    }
}