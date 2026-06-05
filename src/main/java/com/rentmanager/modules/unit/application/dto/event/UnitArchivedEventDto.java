package com.rentmanager.modules.unit.application.dto.event;

import java.util.UUID;

public class UnitArchivedEventDto extends BaseUnitEventDto {

    private final String reason;

    public UnitArchivedEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId,
            String reason
    ) {
        super(unitId, tenantId, correlationId);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}