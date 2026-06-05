package com.rentmanager.modules.property.infrastructure.persistence.specification;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class PropertySpecification {

    private PropertySpecification() {
    }

    public static Specification<PropertyJpaEntity> hasTenantId(
            UUID tenantId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<PropertyJpaEntity> hasId(
            UUID propertyId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("id"), propertyId);
    }

    public static Specification<PropertyJpaEntity> hasReferenceCode(
            String referenceCode
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("referenceCode"), referenceCode);
    }

    public static Specification<PropertyJpaEntity> hasStatus(
            PropertyStatus status
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<PropertyJpaEntity> belongsToCategory(
            UUID categoryId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<PropertyJpaEntity> nameContains(
            String keyword
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("name")),
                        "%" + keyword.toLowerCase() + "%"
                );
    }

    public static Specification<PropertyJpaEntity> cityContains(
            String city
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("address").get("city")),
                        "%" + city.toLowerCase() + "%"
                );
    }

    public static Specification<PropertyJpaEntity> countryContains(
            String country
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("address").get("country")),
                        "%" + country.toLowerCase() + "%"
                );
    }
}