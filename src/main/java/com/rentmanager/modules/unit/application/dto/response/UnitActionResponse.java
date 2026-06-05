package com.rentmanager.modules.unit.application.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UnitActionResponse {

    private UUID unitId;

    private String status;

    private String message;

    private String correlationId;
}