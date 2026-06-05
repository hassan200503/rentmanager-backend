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

        Property property = Property.create(
                tenantId,
                request.getName(),
                request.getPropertyType(),
                request.getAddress(),
                request.getGeoLocation(),
                request.getDimensions(),
                request.getDescription(),
                generateCorrelationId()
        );

        return propertyMapper.toResponse(propertyRepository.save(property));
    }



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

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    // ---------------- ARCHIVE ----------------
    @Override
    public PropertyResponse archiveProperty(UUID tenantId,
                                            UUID propertyId) {

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        property.archive(generateCorrelationId());

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    // ---------------- ACTIVATE ----------------
    @Override
    public PropertyResponse activateProperty(UUID tenantId,
                                             UUID propertyId) {

        Property property = activatePropertyValidator.validate(tenantId, propertyId);

        property.activate(generateCorrelationId());

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    // ---------------- FULLY OCCUPIED ----------------
    @Override
    public PropertyResponse markFullyOccupied(UUID tenantId,
                                              UUID propertyId) {

        Property property = markFullyOccupiedValidator.validate(tenantId, propertyId);

        property.markFullyOccupied(generateCorrelationId());

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    // ---------------- VACANT ----------------
    @Override
    public PropertyResponse markVacant(UUID tenantId,
                                       UUID propertyId) {

        Property property = propertyMarkVacantValidator.validate(tenantId, propertyId);

        property.markVacant(generateCorrelationId());

        return propertyMapper.toResponse(propertyRepository.save(property));
    }

    // ---------------- INTERNAL ----------------
    private String generateCorrelationId() {
        return "PROP-" + System.currentTimeMillis();
    }
}