package com.rentmanager.modules.unit.application.dto.response;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OccupancyResponse {

    private UUID unitId;

    private UnitOccupancyStatus previousStatus;

    private UnitOccupancyStatus newStatus;

    private String correlationId;
}