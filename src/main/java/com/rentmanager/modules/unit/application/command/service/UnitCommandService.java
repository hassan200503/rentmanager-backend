package com.rentmanager.modules.unit.application.command.service;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;

import java.util.UUID;

public interface UnitCommandService {

    /**
     * Create a new Unit under a tenant
     */
    UnitResponse create(UUID tenantId, CreateUnitRequest request);

    /**
     * Update an existing Unit
     */
    UnitResponse update(UUID tenantId, UUID unitId, UpdateUnitRequest request);

    /**
     * Activate a Unit (make it operational/available in system flow)
     */
    void activate(UUID tenantId, UUID unitId, String correlationId);

    /**
     * Archive a Unit (soft lifecycle removal from active listings)
     */
    void archive(UUID tenantId, UUID unitId);

    /**
     * Mark Unit as occupied (linked to lease lifecycle)
     */
    void markOccupied(UUID tenantId, UUID unitId, String correlationId);

    /**
     * Mark Unit as vacant (available for leasing)
     */
    void markVacant(UUID tenantId, UUID unitId, String correlationId);

    void delete(UUID tenantId, UUID unitId);
}