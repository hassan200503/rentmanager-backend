package com.rentmanager.modules.user.infrastructure.persistence.repository;

import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByClerkUserId(String clerkUserId);
    boolean existsByTenantId(UUID tenantId);
    Page<UserEntity> findByTenantId(UUID tenantId, Pageable pageable);
    List<UserEntity> findByTenantIdAndRole(UUID tenantId, UserRole role);
}