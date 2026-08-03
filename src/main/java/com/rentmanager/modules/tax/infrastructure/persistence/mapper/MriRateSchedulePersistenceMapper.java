package com.rentmanager.modules.tax.infrastructure.persistence.mapper;

import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.MriRateScheduleJpaEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class MriRateSchedulePersistenceMapper {

    public MriRateScheduleJpaEntity toJpaEntity(MriRateSchedule schedule) {
        if (schedule == null) {
            return null;
        }

        MriRateScheduleJpaEntity entity = new MriRateScheduleJpaEntity();
        entity.setEffectiveFrom(schedule.getEffectiveFrom());
        entity.setEffectiveTo(schedule.getEffectiveTo());
        entity.setRatePercent(schedule.getRatePercent());
        entity.setStatus(schedule.getStatus());
        entity.setSourceReference(schedule.getSourceReference());

        setField(entity, "id", schedule.getId());
        setField(entity, "createdAt", schedule.getCreatedAt());
        setField(entity, "updatedAt", schedule.getUpdatedAt());
        setField(entity, "version", schedule.getVersion());
        return entity;
    }

    public MriRateSchedule toDomain(MriRateScheduleJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return MriRateSchedule.rehydrate(
                entity.getId(),
                null,
                entity.getEffectiveFrom(),
                entity.getEffectiveTo(),
                entity.getRatePercent(),
                entity.getStatus(),
                entity.getSourceReference(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map field: " + fieldName, ex);
        }
    }
}
