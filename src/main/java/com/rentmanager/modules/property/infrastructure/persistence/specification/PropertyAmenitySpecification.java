package com.rentmanager.modules.property.infrastructure.persistence.specification;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAmenityJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class PropertyAmenitySpecification {

    private PropertyAmenitySpecification() {
    }

    public static Specification<PropertyAmenityJpaEntity> hasTenantId(
            UUID tenantId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<PropertyAmenityJpaEntity> hasId(
            UUID amenityId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("id"), amenityId);
    }

    public static Specification<PropertyAmenityJpaEntity> hasName(
            String name
    ) {
        return (root, query, cb) ->
                cb.equal(
                        cb.lower(root.get("name")),
                        name.toLowerCase()
                );
    }

    public static Specification<PropertyAmenityJpaEntity> nameContains(
            String keyword
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("name")),
                        "%" + keyword.toLowerCase() + "%"
                );
    }
}