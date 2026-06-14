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

    private String label; // ✅ FIXED (missing)

    private BigDecimal rentAmount;

    private String description;
}