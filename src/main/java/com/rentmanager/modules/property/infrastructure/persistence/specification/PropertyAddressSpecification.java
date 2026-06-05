package com.rentmanager.modules.property.infrastructure.persistence.specification;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class PropertyAddressSpecification {

    private PropertyAddressSpecification() {
        // utility class
    }

    public static Specification<PropertyAddressJpaEntity> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                tenantId == null ? null : cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<PropertyAddressJpaEntity> hasCity(String city) {
        return (root, query, cb) ->
                (city == null || city.isBlank()) ? null : cb.equal(root.get("city"), city);
    }

    public static Specification<PropertyAddressJpaEntity> hasCountry(String country) {
        return (root, query, cb) ->
                (country == null || country.isBlank()) ? null : cb.equal(root.get("country"), country);
    }

    public static Specification<PropertyAddressJpaEntity> hasStreetAddressLike(String streetAddress) {
        return (root, query, cb) ->
                (streetAddress == null || streetAddress.isBlank())
                        ? null
                        : cb.like(cb.lower(root.get("streetAddress")),
                                  "%" + streetAddress.toLowerCase() + "%");
    }

    public static Specification<PropertyAddressJpaEntity> hasPropertyId(UUID propertyId) {
        return (root, query, cb) ->
                propertyId == null ? null : cb.equal(root.get("property").get("id"), propertyId);
    }
}