package com.rentmanager.modules.auth.api.request;

public record RefreshRequest(
        String refreshToken
) {
}