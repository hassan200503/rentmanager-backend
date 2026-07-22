package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.unit.domain.event.UnitActivatedEvent;
import com.rentmanager.modules.unit.domain.event.UnitArchivedEvent;
import com.rentmanager.modules.unit.domain.event.UnitCreatedEvent;
import com.rentmanager.modules.unit.domain.event.UnitOccupancyChangedEvent;
import com.rentmanager.modules.unit.domain.event.UnitUpdatedEvent;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.security.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnitActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(UnitActivityEventListener.class);

    private final ActivityLogService activityLogService;
    private final UnitRepository unitRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitCreated(UnitCreatedEvent event) {
        log.debug("onUnitCreated FIRED for unit {}", event.getUnitId());
        Optional<Unit> unit = unitRepository.findById(event.getUnitId());
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_CREATED", event.getUnitId());
            return;
        }

        Unit u = unit.get();
        record(event.getTenantId(), event.eventType(), event.getUnitId(), displayName(u), metadataWithProperty(u));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitUpdated(UnitUpdatedEvent event) {
        log.debug("onUnitUpdated FIRED for unit {}", event.getUnitId());
        Optional<Unit> unit = unitRepository.findById(event.getUnitId());
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_UPDATED", event.getUnitId());
            return;
        }

        Unit u = unit.get();
        String displayName = displayName(u);

        record(event.getTenantId(), event.eventType(), event.getUnitId(), displayName,
                metadataWithProperty(u, Map.of("rentAmount", event.getRentAmount())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitActivated(UnitActivatedEvent event) {
        log.debug("onUnitActivated FIRED for unit {}", event.getUnitId());
        Optional<Unit> unit = unitRepository.findById(event.getUnitId());
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_ACTIVATED", event.getUnitId());
            return;
        }

        Unit u = unit.get();
        record(event.getTenantId(), event.eventType(), event.getUnitId(), displayName(u), metadataWithProperty(u));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitArchived(UnitArchivedEvent event) {
        log.debug("onUnitArchived FIRED for unit {}", event.getUnitId());
        UUID unitId = event.getUnitId();
        Optional<Unit> unit = unitRepository.findById(unitId);
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_ARCHIVED", unitId);
            return;
        }

        Unit u = unit.get();
        record(event.getTenantId(), event.eventType(), unitId, displayName(u), metadataWithProperty(u));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitOccupancyChanged(UnitOccupancyChangedEvent event) {
        log.debug("onUnitOccupancyChanged FIRED for unit {}", event.getUnitId());
        Optional<Unit> unit = unitRepository.findById(event.getUnitId());
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_OCCUPANCY_CHANGED", event.getUnitId());
            return;
        }

        Unit u = unit.get();
        Map<String, Object> metadata = metadataWithProperty(u);
        metadata.put("previousOccupancy", event.getPreviousStatus().name());
        metadata.put("newOccupancy", event.getNewStatus().name());

        record(event.getTenantId(), event.eventType(), event.getUnitId(), displayName(u), metadata);
    }

    // ---- helpers ----

    private String displayName(Unit unit) {
        return unit.getLabel() != null ? unit.getLabel() : unit.getUnitNumber();
    }

    private static Map<String, Object> metadataWithProperty(Unit unit) {
        Map<String, Object> m = new java.util.HashMap<>();
        if (unit.getPropertyId() != null) {
            m.put("propertyId", unit.getPropertyId().toString());
        }
        return m;
    }

    private static Map<String, Object> metadataWithProperty(Unit unit, Map<String, Object> extra) {
        Map<String, Object> m = metadataWithProperty(unit);
        m.putAll(extra);
        return m;
    }

    private void record(UUID tenantId, String eventType, UUID unitId, String displayName,
                        Map<String, Object> metadata) {
        try {
            activityLogService.record(
                    tenantId,
                    eventType,
                    "Unit",
                    unitId,
                    displayName,
                    actorId(),
                    actorName(),
                    metadata
            );
        } catch (Exception ex) {
            // Post-commit: the unit mutation already succeeded. Log at ERROR so it
            // surfaces in monitoring rather than being silently swallowed.
            log.error("Failed to write activity log for event {} on unit {}", eventType, unitId, ex);
        }
    }

    private UUID actorId() {
        var context = SecurityContextHolder.get();
        return context != null ? context.userId() : null;
    }

    private String actorName() {
        var context = SecurityContextHolder.get();
        return context != null && context.email() != null ? context.email() : "System";
    }
}
