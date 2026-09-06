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
    /** Carried so the renter's notification can name the request they reported. */
    private final String title;
    /** The landlord's message to the renter, or null when they said nothing. */
    private final String landlordNote;

    public MaintenanceRequestStatusChanged(
            UUID tenantId,
            UUID requestId,
            String correlationId,
            UUID tenantProfileId,
            MaintenanceRequestStatus oldStatus,
            MaintenanceRequestStatus newStatus,
            String title,
            String landlordNote
    ) {
        super(tenantId, requestId, correlationId);
        this.requestId = requestId;
        this.tenantProfileId = tenantProfileId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.title = title;
        this.landlordNote = landlordNote;
    }

    @Override
    public String eventType() {
        return "MaintenanceRequestStatusChanged";
    }
}
