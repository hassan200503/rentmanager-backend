package com.rentmanager.modules.property.application.command.service;

import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PropertyCommandServiceImpl implements PropertyCommandService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;
    private final DomainEventPublisher eventPublisher;

    private final CreatePropertyValidator createPropertyValidator;
    private final UpdatePropertyValidator updatePropertyValidator;
    private final ActivatePropertyValidator activatePropertyValidator;
    private final MarkFullyOccupiedValidator markFullyOccupiedValidator;
    private final PropertyMarkVacantValidator propertyMarkVacantValidator;

    private final PropertyMarkPartiallyOccupiedValidator markPartiallyOccupiedValidator;

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

        // NOTE: pull events from `property` (the aggregate that had registerEvent()
        // called on it), not `saved` — PropertyRepositoryAdapter.save() returns a
        // freshly remapped instance via persistenceMapper.toDomain(...), which has
        // its own empty domainEvents list. Pulling from `saved` always publishes
        // nothing, silently.
        eventPublisher.publishAll(property.pullDomainEvents());

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

        Property saved = propertyRepository.save(property);

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

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

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

        return propertyMapper.toResponse(saved);
    }

    // ---------------- ACTIVATE ----------------
    @Override
    public PropertyResponse activateProperty(UUID tenantId,
                                             UUID propertyId) {

        Property property = activatePropertyValidator.validate(tenantId, propertyId);

        property.activate(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

        return propertyMapper.toResponse(saved);
    }

    // ---------------- FULLY OCCUPIED ----------------
    @Override
    public PropertyResponse markFullyOccupied(UUID tenantId,
                                              UUID propertyId) {

        Property property = markFullyOccupiedValidator.validate(tenantId, propertyId);

        property.markFullyOccupied(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

        return propertyMapper.toResponse(saved);
    }

    // ---------------- VACANT ----------------
    @Override
    public PropertyResponse markVacant(UUID tenantId,
                                       UUID propertyId) {

        Property property = propertyMarkVacantValidator.validate(tenantId, propertyId);

        property.markVacant(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

        return propertyMapper.toResponse(saved);
    }

    // ---------------- INTERNAL ----------------
    private String generateCorrelationId() {
        return "PROP-" + UUID.randomUUID();
    }








    // ---------------- PARTIALLY OCCUPIED ----------------
    @Override
    public PropertyResponse markPartiallyOccupied(UUID tenantId,
                                                  UUID propertyId) {

        Property property = markPartiallyOccupiedValidator.validate(tenantId, propertyId);

        property.markPartiallyOccupied(generateCorrelationId());

        Property saved = propertyRepository.save(property);

        // See note in createProperty(): pull from `property`, not `saved`.
        eventPublisher.publishAll(property.pullDomainEvents());

        return propertyMapper.toResponse(saved);
    }
}