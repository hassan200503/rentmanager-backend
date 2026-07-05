package com.rentmanager.modules.user.application.dto.response;

import com.rentmanager.modules.user.domain.model.UserRole;

import java.util.UUID;

public record InviteUserResponse(
        UUID userId,
        String email,
        UserRole role
) {}