package com.rentmanager.modules.auth.api.response;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}