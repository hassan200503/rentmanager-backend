package com.rentmanager.modules.unit.application.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class CreateUnitRequest {

    private UUID propertyId;

    private String unitNumber;

    private String label; // ✅ FIXED (missing)

    private BigDecimal rentAmount;

    private String description;
}