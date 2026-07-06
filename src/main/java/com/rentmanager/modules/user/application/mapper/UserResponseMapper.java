package com.rentmanager.modules.user.application.mapper;

import com.rentmanager.modules.user.application.dto.response.UserResponse;
import com.rentmanager.modules.user.domain.model.User;

public final class UserResponseMapper {

    private UserResponseMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isActive()
        );
    }
}