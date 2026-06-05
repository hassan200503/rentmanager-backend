package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class OccupancyResponse {

    private UUID propertyId;

    private OccupancyStatus previousStatus;

    private OccupancyStatus newStatus;

    private String correlationId;
}