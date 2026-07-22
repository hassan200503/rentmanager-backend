package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.lease.domain.event.LeaseCreatedEvent;
import com.rentmanager.modules.lease.domain.event.LeaseTerminatedEvent;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
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
 * NOTE: only LeaseCreatedEvent and LeaseTerminatedEvent are wired here.
 * LeaseApprovedEvent, LeaseActivatedEvent, LeaseRenewedEvent, and
 * LeaseCancelledEvent are NOT wired yet — per the comment in Lease.expire(),
 * those five event types have a pre-existing double-publish bug (fired once
 * via the aggregate's own event list, and again directly by
 * LeaseWorkflowEngine). Wiring listeners for them now would produce two
 * activity-feed rows per real action. Add them once that bug is fixed.
 */
@Component
@RequiredArgsConstructor
public class LeaseActivityEventListener {

    private static final Logger log = LoggerFactory.getLogger(LeaseActivityEventListener.class);

    private final ActivityLogService activityLogService;
    private final LeaseRepository leaseRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaseCreated(LeaseCreatedEvent event) {
        record(event.getTenantId(), event.eventType(), event.getAggregateId(),
                Map.of("status", event.getStatus()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLeaseTerminated(LeaseTerminatedEvent event) {
        record(event.getTenantId(), event.eventType(), event.getAggregateId(),
                Map.of(
                        "terminationType", event.getTerminationType(),
                        "terminationReason", event.getTerminationReason()
                ));
    }

    private void record(UUID tenantId, String eventType, UUID leaseId, Map<String, Object> metadata) {
        Optional<Lease> lease = leaseRepository.findById(leaseId);
        if (lease.isEmpty()) {
            log.warn("Activity log: lease {} not found after commit for event {}", leaseId, eventType);
            return;
        }

        Lease l = lease.get();
        Map<String, Object> enriched = new java.util.HashMap<>(metadata);
        if (l.getPropertyId() != null) enriched.put("propertyId", l.getPropertyId().toString());
        if (l.getUnitId() != null) enriched.put("unitId", l.getUnitId().toString());

        var context = SecurityContextHolder.get();
        UUID actorId = context != null ? context.userId() : null;
        String actorName = context != null && context.email() != null ? context.email() : "System";

        activityLogService.record(
                tenantId,
                eventType,
                "Lease",
                leaseId,
                l.getLeaseNumber(),
                actorId,
                actorName,
                enriched
        );
    }
}