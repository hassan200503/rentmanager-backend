package com.rentmanager.modules.auth.infrastructure.persistence;

import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;
import com.rentmanager.modules.auth.infrastructure.repository.AuthUserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuthUserPersistenceAdapter {

    private final AuthUserRepository repository;

    public AuthUserPersistenceAdapter(AuthUserRepository repository) {
        this.repository = repository;
    }

    public AuthUserEntity save(AuthUserEntity entity) {
        return repository.save(entity);
    }

    public Optional<AuthUserEntity> findByTenantAndEmail(UUID tenantId, String email) {
        return repository.findByTenantIdAndEmail(tenantId, email);
    }

    public Optional<AuthUserEntity> findById(UUID id) {
        return repository.findById(id);
    }
}