package com.rentmanager.modules.unit.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitReservationSummaryResponse {

    private UUID unitId;
    private String unitNumber;
    private String propertyName;
    private BigDecimal monthlyRent;
    private BigDecimal depositAmount;
}