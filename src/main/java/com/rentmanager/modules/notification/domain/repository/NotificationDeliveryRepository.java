package com.rentmanager.modules.notification.domain.repository;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;

import java.time.Instant;
import java.util.List;

public interface NotificationDeliveryRepository {

    NotificationDelivery save(NotificationDelivery delivery);

    /**
     * Rows due for dispatch (PENDING, or FAILED with next_attempt_at in
     * the past - i.e. the backoff has elapsed), ordered by next_attempt_at,
     * capped for one sweep. SENT/GAVE_UP rows are never returned.
     */
    List<NotificationDelivery> findDue(Instant now, int limit);
}
