package com.rentmanager.modules.unit.infrastructure.persistence.mapper;

import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitMediaJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UnitMediaPersistenceMapper {

    // =========================
    // DOMAIN → JPA
    // =========================
    public UnitMediaJpaEntity toJpaEntity(UnitMedia media) {
        if (media == null) return null;

        return UnitMediaJpaEntity.builder()
                .id(media.getId())
                .tenantId(media.getTenantId())
                .unitId(media.getUnitId())
                .url(media.getUrl())
                .type(media.getType())
                .caption(media.getCaption())
                .primaryMedia(media.isPrimary())
                .sortOrder(media.getSortOrder())
                .build();
    }

    // =========================
    // JPA → DOMAIN
    // =========================
    public UnitMedia toDomain(UnitMediaJpaEntity entity) {
        if (entity == null) return null;

        return UnitMedia.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getUnitId(),
                entity.getUrl(),
                entity.getType(),
                entity.getCaption(),
                entity.isPrimaryMedia(),
                entity.getSortOrder()
        );
    }
}