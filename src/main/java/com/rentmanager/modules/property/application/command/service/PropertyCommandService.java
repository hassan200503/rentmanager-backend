package com.rentmanager.modules.property.application.command.service;

import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;

import java.util.UUID;

public interface PropertyCommandService {

    PropertyResponse createProperty(UUID tenantId, CreatePropertyRequest request);

    PropertyResponse updateProperty(UUID tenantId, UUID propertyId, UpdatePropertyRequest request);

    PropertyResponse activateProperty(UUID tenantId, UUID propertyId);

    PropertyResponse archiveProperty(UUID tenantId, UUID propertyId);

    PropertyResponse getProperty(UUID tenantId, UUID propertyId);

    PropertyResponse markFullyOccupied(UUID tenantId, UUID propertyId);

    PropertyResponse markVacant(UUID tenantId, UUID propertyId);


    PropertyResponse markPartiallyOccupied(UUID tenantId, UUID propertyId);

    void deleteProperty(UUID tenantId, UUID propertyId);
}