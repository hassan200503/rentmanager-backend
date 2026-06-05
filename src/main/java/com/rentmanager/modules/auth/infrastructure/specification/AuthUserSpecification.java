package com.rentmanager.modules.auth.infrastructure.specification;

import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class AuthUserSpecification {

    public static Specification<AuthUserEntity> hasTenantId(UUID tenantId) {
        return (root, query, cb) ->
                cb.equal(root.get("tenantId"), tenantId);
    }

    public static Specification<AuthUserEntity> hasEmail(String email) {
        return (root, query, cb) ->
                cb.equal(root.get("email"), email);
    }

    public static Specification<AuthUserEntity> isActive(boolean active) {
        return (root, query, cb) ->
                cb.equal(root.get("active"), active);
    }
}