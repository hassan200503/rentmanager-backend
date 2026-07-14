package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.event.PropertyActivatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyArchivedEvent;
import com.rentmanager.modules.property.domain.event.PropertyCreatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyOccupancyChangedEvent;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;

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

/**
 * Bridges existing Property domain events into the activity feed, without any
 * change to Property, PropertyService, or the events themselves.
 *
 * NOTE: adjust the PropertyRepository import/method above if your actual
 * repository interface or package differs from this assumption.
 */
@Component
@RequiredArgsConstructor
public class PropertyActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(PropertyActivityEventListener.class);

    private final ActivityLogService activityLogService;
    private final PropertyRepository propertyRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyCreated(PropertyCreatedEvent event) {
        record(event.getTenantId(), event.eventType(), event.getPropertyId(), Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyActivated(PropertyActivatedEvent event) {
        record(event.getTenantId(), event.eventType(), event.getAggregateId(), Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyArchived(PropertyArchivedEvent event) {
        record(event.getTenantId(), event.eventType(), event.getAggregateId(), Map.of());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyOccupancyChanged(PropertyOccupancyChangedEvent event) {
        Map<String, Object> metadata = Map.of(
                "previousOccupancy", occupancyName(event.getPreviousOccupancy()),
                "newOccupancy", occupancyName(event.getNewOccupancy())
        );

        record(event.getTenantId(), event.eventType(), event.getAggregateId(), metadata);
    }

    private String occupancyName(int ordinal) {
        OccupancyStatus[] values = OccupancyStatus.values();
        if (ordinal < 0 || ordinal >= values.length) {
            log.warn("Unrecognized OccupancyStatus ordinal {} on activity event; recording as UNKNOWN", ordinal);
            return "UNKNOWN";
        }
        return values[ordinal].name();
    }

    private void record(UUID tenantId, String eventType, UUID propertyId, Map<String, Object> metadata) {

        Optional<Property> property = propertyRepository.findById(propertyId);
        if (property.isEmpty()) {
            // Shouldn't happen post-commit, but don't let a missing lookup break the caller's flow.
            log.warn("Activity log: property {} not found after commit for event {}", propertyId, eventType);
            return;
        }

        var context = SecurityContextHolder.get();
        UUID actorId = context != null ? context.userId() : null;
        String actorName = context != null && context.email() != null ? context.email() : "System";

        try {
            activityLogService.record(
                    tenantId,
                    eventType,
                    "Property",
                    propertyId,
                    property.get().getName(),
                    actorId,
                    actorName,
                    metadata
            );
        } catch (Exception ex) {
            // This runs post-commit (AFTER_COMMIT phase): the property mutation has
            // already succeeded and cannot be rolled back from here. A failure to
            // write the activity log must not propagate and must not be silent —
            // log at ERROR so it surfaces in monitoring/alerting instead of being
            // swallowed by Spring's internal afterCommit exception handling.
            log.error("Failed to write activity log for event {} on property {}", eventType, propertyId, ex);
        }
    }
}