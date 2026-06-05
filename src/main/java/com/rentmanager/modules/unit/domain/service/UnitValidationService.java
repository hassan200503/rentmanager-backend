package com.rentmanager.modules.unit.domain.service;

import com.rentmanager.modules.unit.domain.model.Unit;

public interface UnitValidationService {

    void validateCreation(Unit unit);

    void validateActivation(Unit unit);

    void validateOccupancyChange(Unit unit);
}