package com.rentmanager.modules.notification.infrastructure.persistence.repository;

import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import com.rentmanager.modules.notification.infrastructure.persistence.entity.NotificationDeliveryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryJpaRepository extends JpaRepository<NotificationDeliveryJpaEntity, UUID> {

    /**
     * Due rows: PENDING (never dispatched) plus FAILED (retry after
     * backoff has elapsed). SENT and GAVE_UP are terminal and excluded.
     */
    List<NotificationDeliveryJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
            Collection<NotificationDeliveryStatus> statuses, Instant now);
}
