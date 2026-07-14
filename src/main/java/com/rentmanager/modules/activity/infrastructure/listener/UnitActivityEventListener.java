package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.unit.domain.event.UnitCreatedEvent;
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

/**
 * NOTE: only UnitCreatedEvent and UnitUpdatedEvent are wired here — those are
 * the two Unit events whose full class definitions have been confirmed.
 * UnitActivatedEvent, UnitDeactivatedEvent, UnitOccupancyChangedEvent, and
 * UnitArchivedEvent are used in Unit.java but their field/getter shapes
 * haven't been shared yet, so adding them here would risk guessing wrong.
 */
@Component
@RequiredArgsConstructor
public class UnitActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(UnitActivityEventListener.class);

    private final ActivityLogService activityLogService;
    private final UnitRepository unitRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitCreated(UnitCreatedEvent event) {
        Optional<Unit> unit = unitRepository.findById(event.getUnitId());
        if (unit.isEmpty()) {
            log.warn("Activity log: unit {} not found after commit for UNIT_CREATED", event.getUnitId());
            return;
        }

        String displayName = unit.get().getLabel() != null ? unit.get().getLabel() : unit.get().getUnitNumber();

        activityLogService.record(
                event.getTenantId(),
                event.eventType(),
                "Unit",
                event.getUnitId(),
                displayName,
                actorId(),
                actorName(),
                Map.of()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitUpdated(UnitUpdatedEvent event) {
        // The event itself already carries the updated label/number, so no
        // repository lookup is needed here.
        String displayName = event.getLabel() != null ? event.getLabel() : event.getUnitNumber();

        activityLogService.record(
                event.getTenantId(),
                event.eventType(),
                "Unit",
                event.getUnitId(),
                displayName,
                actorId(),
                actorName(),
                Map.of("rentAmount", event.getRentAmount())
        );
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