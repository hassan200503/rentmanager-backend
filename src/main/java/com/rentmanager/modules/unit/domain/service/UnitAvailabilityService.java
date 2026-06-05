package com.rentmanager.modules.unit.domain.service;

import java.util.UUID;

public interface UnitAvailabilityService {

    boolean isAvailable(UUID tenantId, UUID unitId);
}