package com.rentmanager.modules.auth.application.usecase;

import com.rentmanager.modules.auth.application.service.AuthPasswordService;
import com.rentmanager.modules.auth.application.service.AuthTokenService;
import com.rentmanager.modules.auth.infrastructure.entity.AuthUserEntity;
import com.rentmanager.modules.auth.infrastructure.persistence.AuthUserPersistenceAdapter;
import com.rentmanager.shared.security.context.TenantContext;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoginUserUseCase {

    private final AuthUserPersistenceAdapter persistence;
    private final AuthPasswordService authPasswordService;
    private final AuthTokenService authTokenService;

    public LoginUserUseCase(
            AuthUserPersistenceAdapter persistence,
            AuthPasswordService authPasswordService,
            AuthTokenService authTokenService
    ) {
        this.persistence = persistence;
        this.authPasswordService = authPasswordService;
        this.authTokenService = authTokenService;
    }

    public LoginResponse execute(String email, String password) {

        UUID tenantId = TenantContext.getTenantId();

        AuthUserEntity user = persistence
                .findByTenantAndEmail(tenantId, email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!authPasswordService.matches(password, user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String accessToken = authTokenService.generateAccessToken(
                user.getId(),
                tenantId,
                user.getEmail()

        );

        String refreshToken = authTokenService.generateRefreshToken(
                user.getId(),
                tenantId,
                user.getEmail()

        );

        return new LoginResponse(accessToken, refreshToken);
    }

    // =========================
    // RESPONSE DTO (SaaS-grade)
    // =========================
    public record LoginResponse(
            String accessToken,
            String refreshToken
    ) {}
}