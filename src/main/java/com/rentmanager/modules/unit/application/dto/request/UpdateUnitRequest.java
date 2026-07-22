package com.rentmanager.modules.unit.application.dto.request;

import java.math.BigDecimal;

import lombok.*;


@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class UpdateUnitRequest {

    private String unitNumber;

    private String label;

    private String floor;

    private BigDecimal rentAmount;

    private BigDecimal depositAmount;

    private String description;
}