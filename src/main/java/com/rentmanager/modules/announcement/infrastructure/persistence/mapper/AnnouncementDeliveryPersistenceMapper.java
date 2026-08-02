package com.rentmanager.modules.announcement.infrastructure.persistence.mapper;

import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementDeliveryJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementDeliveryPersistenceMapper {

    public AnnouncementDeliveryJpaEntity toJpaEntity(AnnouncementDelivery delivery) {
        AnnouncementDeliveryJpaEntity jpa = new AnnouncementDeliveryJpaEntity(
                delivery.getAnnouncementId(),
                delivery.getRenterProfileId(),
                delivery.getChannel(),
                delivery.getStatus(),
                delivery.getSentAt(),
                delivery.getReadAt(),
                delivery.getAttemptCount(),
                delivery.getNextAttemptAt(),
                delivery.getLastError()
        );
        jpa.restoreId(delivery.getId());
        jpa.assignTenantIfUnset(delivery.getTenantId());
        jpa.setVersion(delivery.getVersion());
        return jpa;
    }

    public AnnouncementDelivery toDomain(AnnouncementDeliveryJpaEntity jpa) {
        return AnnouncementDelivery.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getAnnouncementId(),
                jpa.getRenterProfileId(),
                jpa.getChannel(),
                jpa.getStatus(),
                jpa.getSentAt(),
                jpa.getReadAt(),
                jpa.getAttemptCount(),
                jpa.getNextAttemptAt(),
                jpa.getLastError(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt(),
                jpa.getVersion()
        );
    }
}
