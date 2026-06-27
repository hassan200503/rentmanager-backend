package com.rentmanager.modules.unit.infrastructure.persistence.mapper;

import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UnitPersistenceMapper {

    // =========================
    // DOMAIN → JPA
    // =========================
    public UnitJpaEntity toJpaEntity(Unit unit) {

        if (unit == null) return null;

        return UnitJpaEntity.builder()
                .id(unit.getId())
                .tenantId(unit.getTenantId())
                .propertyId(unit.getPropertyId())
                .unitNumber(unit.getUnitNumber())
                .label(unit.getLabel())
                .status(unit.getStatus())
                .occupancyStatus(unit.getOccupancyStatus())
                .rentAmount(unit.getRentAmount())
                .description(unit.getDescription())
                .vacatedAt(unit.getVacatedAt())
                .build();
    }

    // =========================
    // JPA → DOMAIN
    // =========================
    public Unit toDomain(UnitJpaEntity entity) {

        if (entity == null) return null;

        return Unit.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getPropertyId(),
                entity.getUnitNumber(),
                entity.getLabel(),
                entity.getStatus(),
                entity.getOccupancyStatus(),
                entity.getRentAmount(),
                entity.getDescription(),
                entity.getVacatedAt()
        );
    }
}