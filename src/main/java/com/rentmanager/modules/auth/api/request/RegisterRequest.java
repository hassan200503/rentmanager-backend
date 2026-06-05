package com.rentmanager.modules.auth.api.request;

import java.util.UUID;

public record RegisterRequest(
        String email,
        String password,
        UUID tenantId
) {
}