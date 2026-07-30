package com.rentmanager.modules.maintenance.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MaintenanceRequestSubmitted extends DomainEvent {

    private final UUID requestId;
    private final UUID unitId;
    private final UUID propertyId;
    private final UUID tenantProfileId;
    private final UUID leaseId;
    private final String title;
    private final MaintenanceCategory category;
    private final MaintenancePriority priority;

    public MaintenanceRequestSubmitted(
            UUID tenantId,
            UUID requestId,
            String correlationId,
            UUID unitId,
            UUID propertyId,
            UUID tenantProfileId,
            UUID leaseId,
            String title,
            MaintenanceCategory category,
            MaintenancePriority priority
    ) {
        super(tenantId, requestId, correlationId);
        this.requestId = requestId;
        this.unitId = unitId;
        this.propertyId = propertyId;
        this.tenantProfileId = tenantProfileId;
        this.leaseId = leaseId;
        this.title = title;
        this.category = category;
        this.priority = priority;
    }

    @Override
    public String eventType() {
        return "MaintenanceRequestSubmitted";
    }
}
