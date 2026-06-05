package com.rentmanager.modules.unit.infrastructure.persistence.mapper;

import com.rentmanager.modules.unit.domain.model.UnitAmenity;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitAmenityJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UnitAmenityPersistenceMapper {

    // =====================================================
    // DOMAIN → JPA
    // =====================================================
    public UnitAmenityJpaEntity toJpaEntity(UnitAmenity amenity) {

        if (amenity == null) return null;

        return UnitAmenityJpaEntity.builder()
                .id(amenity.getId())
                .tenantId(amenity.getTenantId())
                .unitId(amenity.getUnitId())
                .name(amenity.getName())
                .description(amenity.getDescription())
                .active(amenity.isActive())
                .build();
    }

    // =====================================================
    // JPA → DOMAIN
    // =====================================================
    public UnitAmenity toDomain(UnitAmenityJpaEntity entity) {

        if (entity == null) return null;

        return UnitAmenity.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getUnitId(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive()
        );
    }
}