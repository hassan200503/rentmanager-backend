package com.rentmanager.modules.auth.application.usecase;

import com.rentmanager.modules.auth.application.service.AuthPasswordService;
import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;
import com.rentmanager.modules.auth.infrastructure.persistence.AuthUserPersistenceAdapter;
import com.rentmanager.shared.security.context.TenantContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;


@Service
public class RegisterUserUseCase {

    private final AuthUserPersistenceAdapter persistence;
    private final AuthPasswordService authPasswordService;

    public RegisterUserUseCase(
            AuthUserPersistenceAdapter persistence,
            AuthPasswordService authPasswordService
    ) {
        this.persistence = persistence;
        this.authPasswordService = authPasswordService;
    }

    public void execute(String email, String password) {

        UUID tenantId = TenantContext.getTenantId();

        boolean exists = persistence
                .findByTenantAndEmail(tenantId, email)
                .isPresent();

        if (exists) {
            throw new RuntimeException("User already exists");
        }

        AuthUserEntity entity = new AuthUserEntity();

        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setEmail(email);
        entity.setPassword(authPasswordService.hash(password));
        entity.setActive(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        persistence.save(entity);
    }
}