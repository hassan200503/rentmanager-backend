package com.rentmanager.modules.property.infrastructure.persistence.specification;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyMediaJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class PropertyMediaSpecification {

    private PropertyMediaSpecification() {
    }

    public static Specification<PropertyMediaJpaEntity> hasTenantId(
            UUID tenantId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<PropertyMediaJpaEntity> hasId(
            UUID mediaId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("id"), mediaId);
    }

    public static Specification<PropertyMediaJpaEntity> belongsToProperty(
            UUID propertyId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("propertyId"), propertyId);
    }

    public static Specification<PropertyMediaJpaEntity> hasContentType(
            String contentType
    ) {
        return (root, query, cb) ->
                cb.equal(
                        cb.lower(root.get("contentType")),
                        contentType.toLowerCase()
                );
    }

    public static Specification<PropertyMediaJpaEntity> isPrimaryMedia(
            Boolean primary
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("primaryMedia"), primary);
    }
}