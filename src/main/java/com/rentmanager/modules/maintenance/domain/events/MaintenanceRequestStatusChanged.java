package com.rentmanager.modules.maintenance.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MaintenanceRequestStatusChanged extends DomainEvent {

    private final UUID requestId;
    private final UUID tenantProfileId;
    private final MaintenanceRequestStatus oldStatus;
    private final MaintenanceRequestStatus newStatus;

    public MaintenanceRequestStatusChanged(
            UUID tenantId,
            UUID requestId,
            String correlationId,
            UUID tenantProfileId,
            MaintenanceRequestStatus oldStatus,
            MaintenanceRequestStatus newStatus
    ) {
        super(tenantId, requestId, correlationId);
        this.requestId = requestId;
        this.tenantProfileId = tenantProfileId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    @Override
    public String eventType() {
        return "MaintenanceRequestStatusChanged";
    }
}
