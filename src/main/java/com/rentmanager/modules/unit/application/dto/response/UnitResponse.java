package com.rentmanager.modules.unit.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class UnitResponse {

    private UUID id;
    private UUID tenantId;
    private UUID propertyId;

    private String unitNumber;
    private Integer floor;
    private String description;
    private BigDecimal rentAmount;

    private String status;
    private String occupancyStatus; // ✅ ADD THIS
}