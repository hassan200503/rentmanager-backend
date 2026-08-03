package com.rentmanager.modules.tax.infrastructure.persistence.mapper;

import com.rentmanager.modules.tax.domain.model.PropertyTaxRegistration;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.PropertyTaxRegistrationJpaEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class PropertyTaxRegistrationPersistenceMapper {

    public PropertyTaxRegistrationJpaEntity toJpaEntity(PropertyTaxRegistration registration) {
        if (registration == null) {
            return null;
        }

        PropertyTaxRegistrationJpaEntity entity = new PropertyTaxRegistrationJpaEntity();
        entity.assignTenantIfUnset(registration.getTenantId());
        entity.setPropertyId(registration.getPropertyId());
        entity.setLandlordKraPin(registration.getLandlordKraPin());
        entity.setTenantKraPin(registration.getTenantKraPin());
        entity.setKrPropertyRegistrationId(registration.getKrPropertyRegistrationId());
        entity.setStatus(registration.getStatus());
        entity.setRegisteredAt(registration.getRegisteredAt());
        entity.setLastError(registration.getLastError());

        setField(entity, "id", registration.getId());
        setField(entity, "createdAt", registration.getCreatedAt());
        setField(entity, "updatedAt", registration.getUpdatedAt());
        setField(entity, "version", registration.getVersion());
        return entity;
    }

    public PropertyTaxRegistration toDomain(PropertyTaxRegistrationJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return PropertyTaxRegistration.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getPropertyId(),
                entity.getLandlordKraPin(),
                entity.getTenantKraPin(),
                entity.getKrPropertyRegistrationId(),
                entity.getStatus(),
                entity.getRegisteredAt(),
                entity.getLastError(),
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