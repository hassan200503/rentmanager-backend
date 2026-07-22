package com.rentmanager.modules.unit.application.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class CreateUnitRequest {

    private UUID propertyId;

    private String unitNumber;

    private String label;

    private String floor;

    private BigDecimal rentAmount;

    private BigDecimal depositAmount;

    private String description;
}