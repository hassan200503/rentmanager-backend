package com.rentmanager.modules.property.domain.service;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import java.util.UUID;

public interface PropertyAvailabilityService {

    boolean isAvailable(UUID tenantId, UUID propertyId);

    OccupancyStatus calculateOccupancyStatus(UUID tenantId, UUID propertyId);
}