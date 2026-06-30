package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.application.dto.response.UnitReservationSummaryResponse;

import java.util.UUID;

public interface UnitReservationSummaryQueryService {

    UnitReservationSummaryResponse getSummary(UUID unitId);
}