package com.rentmanager.modules.unit.infrastructure.persistence.specification;

import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class UnitSpecification {

    public static Specification<UnitJpaEntity> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<UnitJpaEntity> hasPropertyId(UUID propertyId) {
        return (root, query, cb) ->
                cb.equal(root.get("propertyId"), propertyId);
    }

    public static Specification<UnitJpaEntity> hasStatus(String status) {
        return (root, query, cb) ->
                cb.equal(root.get("status"), status);
    }

    public static Specification<UnitJpaEntity> search(UUID tenantId, String keyword) {
        return (root, query, cb) -> {

            String pattern = "%" + keyword.toLowerCase() + "%";

            return cb.and(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.or(
                            cb.like(cb.lower(root.get("unitNumber")), pattern),
                            cb.like(cb.lower(root.get("description")), pattern)
                    )
            );
        };
    }
}