package com.rentmanager.modules.unit.factory;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;

import java.math.BigDecimal;
import java.util.UUID;

public class UnitTestDataFactory {

    public static CreateUnitRequest createUnitRequest(UUID propertyId) {
        return CreateUnitRequest.builder()
                .propertyId(propertyId)
                .unitNumber("U-" + System.nanoTime())
                .label("Test Unit")
                .rentAmount(BigDecimal.valueOf(1500))
                .description("Test unit")
                .build();
    }
}