package com.rentmanager.modules.tenant.infrastructure.persistence.specification;


import com.rentmanager.modules.tenant.domain.model.Organization;
import org.springframework.data.jpa.domain.Specification;

public class OrganizationSpecification {

    public static Specification<Organization> searchByName(String name) {
        return (root, query, cb) ->
                name == null ? null : cb.like(cb.lower(root.get("legalName")), "%" + name.toLowerCase() + "%");
    }

    public static Specification<Organization> hasType(String type) {
        return (root, query, cb) ->
                type == null ? null : cb.equal(root.get("organizationType"), type);
    }
}