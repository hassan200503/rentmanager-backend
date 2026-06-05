package com.rentmanager.modules.auth.api.request;

import java.util.UUID;

public record LoginRequest(
        String email,
        String password,
        UUID tenantId
) {
}