package com.rentmanager.modules.auth.infrastructure.repository;

import com.rentmanager.modules.auth.infrastructure.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByToken(String token);

    void deleteByUserId(UUID userId);
}