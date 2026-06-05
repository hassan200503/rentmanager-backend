package com.rentmanager.modules.lease.domain.service;

import java.util.UUID;

public interface UnitOccupancyService {

    /**
     * Checks if a unit is currently occupied by an ACTIVE lease
     */
    boolean isUnitOccupied(UUID unitId);

    /**
     * Validates that unit can be assigned a new ACTIVE lease
     * Throws exception if occupied
     */
    void validateUnitAvailability(UUID unitId);
}