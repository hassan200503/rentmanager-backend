package com.rentmanager.modules.unit.application.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArchiveUnitRequest {

    private String reason;

    private String correlationId;
}