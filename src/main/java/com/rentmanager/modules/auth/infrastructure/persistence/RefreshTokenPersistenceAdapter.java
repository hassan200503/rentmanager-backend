package com.rentmanager.modules.auth.infrastructure.persistence;

import com.rentmanager.modules.auth.infrastructure.entity.RefreshTokenEntity;
import com.rentmanager.modules.auth.infrastructure.repository.RefreshTokenRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RefreshTokenPersistenceAdapter {

    private final RefreshTokenRepository repository;

    public RefreshTokenPersistenceAdapter(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    public RefreshTokenEntity save(RefreshTokenEntity entity) {
        return repository.save(entity);
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        return repository.findByToken(token);
    }

    public void deleteByUserId(UUID userId) {
        repository.deleteByUserId(userId);
    }
}