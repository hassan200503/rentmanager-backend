package com.rentmanager.modules.notification.application;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
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
            } else {
                delivery.recordFailure("Provider did not confirm delivery");
            }
        } catch (Exception e) {
            log.error("Notification delivery {} ({}) failed: {}",
                    delivery.getId(), delivery.getChannel(), e.getMessage());
            delivery.recordFailure(String.valueOf(e.getMessage()));
        }
        deliveryRepository.save(delivery);
    }
}
