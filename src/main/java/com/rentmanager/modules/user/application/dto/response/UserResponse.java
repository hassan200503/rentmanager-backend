package com.rentmanager.modules.user.application.dto.response;

import com.rentmanager.modules.user.domain.model.UserRole;

import java.util.UUID;

public record UserResponse(
        UUID userId,
        UUID tenantId,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        boolean active
) {}