package com.rentmanager.modules.tenant.infrastructure.persistence.specification;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import org.springframework.data.jpa.domain.Specification;

public class TenantSpecification {

    public static Specification<Tenant> hasStatus(String status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Tenant> hasType(String type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("type"), type);
    }

    public static Specification<Tenant> searchByName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }
}