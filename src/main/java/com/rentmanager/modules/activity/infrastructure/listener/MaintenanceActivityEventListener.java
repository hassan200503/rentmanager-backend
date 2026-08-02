package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * V54: records maintenance-request submissions into the activity feed so the
 * landlord dashboard's existing SSE/toast/bell-notification pipeline fires
 * for them exactly like property/unit/lease events.
 *
 * The actor is the renter who submitted the request (displayed by their
 * profile name), NOT the security context - this event is published inside
 * the renter-scoped tenant-portal request, whose context is the renter.
 */
@Component
@RequiredArgsConstructor
public class MaintenanceActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceActivityEventListener.class);

    private final ActivityLogService activityLogService;
    private final TenantProfileRepository tenantProfileRepository;
    private final UnitRepository unitRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaintenanceRequestSubmitted(MaintenanceRequestSubmitted event) {
        try {
            String actorName = tenantProfileRepository.findById(event.getTenantProfileId())
                    .map(TenantProfile::getFullName)
                    .filter(name -> !name.isBlank())
                    .orElse("Renter");

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("unitId", event.getUnitId().toString());
            metadata.put("propertyId", event.getPropertyId().toString());
            metadata.put("category", event.getCategory().name());
            metadata.put("priority", event.getPriority().name());
            unitRepository.findById(event.getUnitId())
                    .map(Unit::getUnitNumber)
                    .filter(number -> !number.isBlank())
                    .ifPresent(number -> metadata.put("unitNumber", number));

            activityLogService.record(
                    event.getTenantId(),
                    "MAINTENANCE_REQUEST_SUBMITTED",
                    "MaintenanceRequest",
                    event.getRequestId(),
                    event.getTitle(),
                    null,
                    actorName,
                    metadata
            );
        } catch (Exception ex) {
            log.error("Failed to record maintenance request activity for event: {}", event.getEventId(), ex);
        }
    }
}
