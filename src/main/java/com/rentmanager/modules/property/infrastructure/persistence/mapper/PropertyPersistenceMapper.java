package com.rentmanager.modules.property.infrastructure.persistence.mapper;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.UUID;

@Component
public class PropertyPersistenceMapper {


    public PropertyJpaEntity toJpaEntity(Property property) {

        if (property == null) return null;

        PropertyJpaEntity entity = new PropertyJpaEntity();

        if (property.getId() != null) {
            entity.setId(property.getId());
        }

        entity.setVersion(property.getVersion());

        UUID tenantId = property.getTenantId();

        if (tenantId == null) {
            throw new IllegalStateException("TenantId must be set before persistence");
        }

        // FIX: was assignTenant(tenantId), which throws IllegalStateException
        // if tenantId is already set on the instance. toJpaEntity() only ever
        // builds a brand-new PropertyJpaEntity today, so that path was never
        // hit — but assignTenantIfUnset(...) makes this safe even if
        // toJpaEntity() is ever reused on a resave/re-map path later, matching
        // the same fix already applied to RentLedgerEntry/RentTransaction.
        entity.assignTenantIfUnset(tenantId);

        // ============================
        // 🔥 FIX: SAFE DEFAULTS
        // ============================
        entity.setStatus(
                property.getStatus() != null
                        ? property.getStatus()
                        : PropertyStatus.DRAFT
        );

        entity.setOccupancyStatus(
                property.getOccupancyStatus() != null
                        ? property.getOccupancyStatus()
                        : OccupancyStatus.VACANT
        );

        entity.setPropertyType(property.getPropertyType());
        entity.setPremisesType(property.getPremisesType());

        entity.setReferenceCode(
                property.getReferenceCode() != null
                        ? property.getReferenceCode()
                        : "PROP-" + System.currentTimeMillis()
        );

        entity.setName(property.getName());
        entity.setDescription(property.getDescription());

        entity.setAddress(toJpaAddress(property.getAddress()));

        return entity;
    }





    public Property toDomain(PropertyJpaEntity entity) {

        if (entity == null) return null;

        Property.PropertyBuilder builder = Property.builder()
                .tenantId(entity.getTenantId())
                .referenceCode(entity.getReferenceCode())
                .name(entity.getName())
                .status(entity.getStatus())
                .propertyType(entity.getPropertyType())
                .premisesType(entity.getPremisesType())
                .occupancyStatus(entity.getOccupancyStatus())
                .address(toDomainAddress(entity.getAddress()))
                .description(entity.getDescription());

        Property property = builder.build();

        setField(property, "id", entity.getId());
        setField(property, "createdAt", entity.getCreatedAt());
        setField(property, "updatedAt", entity.getUpdatedAt());
        setField(property, "version", entity.getVersion());

        return property;
    }

    private static PropertyAddressJpaEntity toJpaAddress(Address address) {
        if (address == null) return null;

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
        if (entity == null) return null;

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

    public static void updateEntity(Property property, PropertyJpaEntity entity) {

        if (property == null || entity == null) return;

        entity.setName(property.getName());
        entity.setDescription(property.getDescription());
        entity.setStatus(property.getStatus());

        // SAFE UPDATES ONLY
        entity.setPropertyType(property.getPropertyType());
        entity.setPremisesType(property.getPremisesType());
        entity.setOccupancyStatus(property.getOccupancyStatus());

        if (property.getAddress() != null) {
            entity.setAddress(toJpaAddress(property.getAddress()));
        }

        // tenant NEVER changes
    }
}