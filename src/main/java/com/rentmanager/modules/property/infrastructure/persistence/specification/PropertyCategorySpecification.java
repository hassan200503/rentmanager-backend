package com.rentmanager.modules.property.infrastructure.persistence.specification;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyCategoryJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class PropertyCategorySpecification {

    private PropertyCategorySpecification() {
    }

    public static Specification<PropertyCategoryJpaEntity> hasTenantId(
            UUID tenantId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<PropertyCategoryJpaEntity> hasId(
            UUID categoryId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("id"), categoryId);
    }

    public static Specification<PropertyCategoryJpaEntity> hasName(
            String name
    ) {
        return (root, query, cb) ->
                cb.equal(
                        cb.lower(root.get("name")),
                        name.toLowerCase()
                );
    }

    public static Specification<PropertyCategoryJpaEntity> nameContains(
            String keyword
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("name")),
                        "%" + keyword.toLowerCase() + "%"
                );
    }
}