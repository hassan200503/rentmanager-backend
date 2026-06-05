package com.rentmanager.modules.unit.application.query.projection;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UnitProjection {

    private UUID unitId;

    private UUID tenantId;

    private String unitNumber;

    private String floor;

    private UnitStatus status;

    private OccupancyStatus occupancyStatus;

    private Double rentAmount;

    private String description;
}