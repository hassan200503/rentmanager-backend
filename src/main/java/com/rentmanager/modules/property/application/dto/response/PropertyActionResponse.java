package com.rentmanager.modules.property.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PropertyActionResponse {

    private UUID propertyId;

    private String status;

    private String message;

    private String correlationId;
}