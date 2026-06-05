package com.rentmanager.modules.auth.infrastructure.repository;

import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, UUID> {

    Optional<AuthUserEntity> findByTenantIdAndEmail(UUID tenantId, String email);
}