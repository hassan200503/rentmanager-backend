package com.rentmanager.modules.auth.application.mapper;

import com.rentmanager.modules.auth.domain.model.AuthUser;
import com.rentmanager.modules.auth.domain.enums.AuthProvider;
import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;

public class AuthUserMapper {

    // Entity → Domain
    public static AuthUser toDomain(AuthUserEntity entity) {
        return new AuthUser(
                entity.getId(),
                entity.getTenantId(),
                entity.getEmail(),
                entity.getPassword(),
                AuthProvider.LOCAL
        );
    }

    // Domain → Entity
    public static AuthUserEntity toEntity(AuthUser domain) {

        AuthUserEntity entity = new AuthUserEntity();

        entity.setId(domain.getId());
        entity.setTenantId(domain.getTenantId());
        entity.setEmail(domain.getEmail());
        entity.setPassword(domain.getPassword());
        entity.setActive(domain.isActive());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());

        return entity;
    }
}