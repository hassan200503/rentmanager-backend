package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.shared.security.context.TenantContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class PropertyPersistenceMapper {

    public PropertyJpaEntity toJpaEntity(Property property) {
        if (property == null) {
            return null;
        }

        PropertyJpaEntity entity = new PropertyJpaEntity();

        setField(entity, "id", property.getId());
        entity.setVersion(property.getVersion());

        // =========================================================
        // SAAS SAFE: enforce tenant at persistence boundary
        // =========================================================
        setField(entity, "tenantId",
                property.getTenantId() != null
                        ? property.getTenantId()
                        : TenantContext.getTenantId()
        );

        entity.setReferenceCode(property.getReferenceCode());
        entity.setName(property.getName());
        entity.setDescription(property.getDescription());
        entity.setStatus(property.getStatus());
        entity.setAddress(toJpaAddress(property.getAddress()));

        return entity;
    }

    public Property toDomain(PropertyJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        Property.PropertyBuilder builder = Property.builder()
                .tenantId(entity.getTenantId())
                .referenceCode(entity.getReferenceCode())
                .name(entity.getName())
                .status(entity.getStatus())
                .address(toDomainAddress(entity.getAddress()))
                .description(entity.getDescription());

        Property property = builder.build();

        setField(property, "id", entity.getId());
        setField(property, "createdAt", entity.getCreatedAt());
        setField(property, "updatedAt", entity.getUpdatedAt());
        setField(property, "version", entity.getVersion());

        return property;
    }

    private PropertyAddressJpaEntity toJpaAddress(Address address) {
        if (address == null) {
            return null;
        }

        return PropertyAddressJpaEntity.builder()
                .addressLine1(address.getStreetAddress())
                .addressLine2(null)
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();
    }

    private Address toDomainAddress(PropertyAddressJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return Address.builder()
                .streetAddress(entity.getAddressLine1())
                .city(entity.getCity())
                .state(entity.getState())
                .postalCode(entity.getPostalCode())
                .country(entity.getCountry())
                .build();
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