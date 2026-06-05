package com.rentmanager.modules.property.application.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArchivePropertyRequest {

    private String reason;

    private String correlationId;
}