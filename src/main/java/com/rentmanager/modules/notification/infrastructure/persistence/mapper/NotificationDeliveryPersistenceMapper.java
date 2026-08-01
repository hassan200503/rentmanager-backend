package com.rentmanager.modules.notification.infrastructure.persistence.mapper;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.infrastructure.persistence.entity.NotificationDeliveryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationDeliveryPersistenceMapper {

    public NotificationDeliveryJpaEntity toJpaEntity(NotificationDelivery delivery) {
        NotificationDeliveryJpaEntity jpa = new NotificationDeliveryJpaEntity(
                delivery.getTenantId(),
                delivery.getEventId(),
                delivery.getChannel(),
                delivery.getRecipient(),
                delivery.getSubject(),
                delivery.getMessage(),
                delivery.getMetadata(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastError()
        );
        jpa.restoreId(delivery.getId());
        jpa.setVersion(delivery.getVersion());
        return jpa;
    }

    public NotificationDelivery toDomain(NotificationDeliveryJpaEntity jpa) {
        return NotificationDelivery.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getEventId(),
                jpa.getChannel(),
                jpa.getRecipient(),
                jpa.getSubject(),
                jpa.getMessage(),
                jpa.getMetadata(),
                jpa.getStatus(),
                jpa.getAttemptCount(),
                jpa.getNextAttemptAt(),
                jpa.getLastError(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt(),
                jpa.getVersion()
        );
    }
}
