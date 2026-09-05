package com.rentmanager.modules.notification.application;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import com.rentmanager.shared.observability.BusinessMetrics;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Dispatches due outbox deliveries (Phase 5). Each delivery is processed
 * in its own transaction scope via the proxied bean: a failure recording
 * for one row can never roll back the SENT statuses of the others.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchSweepService {

    public static final int SWEEP_LIMIT = 100;

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationDispatchService dispatchService;
    private final BusinessMetrics metrics;

    @Transactional
    public void dispatchDue() {
        List<NotificationDelivery> due = deliveryRepository.findDue(Instant.now(), SWEEP_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("Notification sweep dispatching {} due delivery(ies)", due.size());
        for (NotificationDelivery delivery : due) {
            dispatchOne(delivery);
        }
    }

    @Transactional
    public void dispatchOne(NotificationDelivery delivery) {
        try {
            boolean accepted = dispatchService.dispatch(delivery);
            if (accepted) {
                delivery.markSent();
                metrics.notificationDelivered();
            } else {
                delivery.recordFailure("Provider did not confirm delivery");
            }
        } catch (Exception e) {
            log.error("Notification delivery {} ({}) failed: {}",
                    delivery.getId(), delivery.getChannel(), e.getMessage());
            delivery.recordFailure(String.valueOf(e.getMessage()));
        }

        reportIfAbandoned(delivery);
        deliveryRepository.save(delivery);
    }

    /**
     * Says something the moment a delivery is abandoned for good.
     *
     * <p>{@code recordFailure} moves a delivery to {@code GAVE_UP} after
     * {@code MAX_ATTEMPTS}, and until now that was the end of it: the row sat
     * in a terminal state that nothing read, nothing counted and nothing
     * alerted on. A misconfigured SMS provider could therefore stop every
     * rent reminder in the system, and the first symptom would be a landlord
     * asking why collections had dropped — weeks later.
     *
     * <p>The ERROR level is deliberate and so is the wording. This is not a
     * transient failure being retried; it is a message to a real person that
     * will now never be sent, and the log line has to be findable by someone
     * grepping for the thing that went wrong rather than by someone who
     * already suspected it.
     */
    private void reportIfAbandoned(NotificationDelivery delivery) {
        if (delivery.getStatus() != NotificationDeliveryStatus.GAVE_UP) {
            return;
        }
        metrics.notificationExhausted();
        log.error("NOTIFICATION ABANDONED — id={} channel={} tenantId={} attempts={} lastError={}. "
                        + "This message will never be delivered.",
                delivery.getId(), delivery.getChannel(), delivery.getTenantId(),
                delivery.getAttemptCount(), delivery.getLastError());
    }
}
