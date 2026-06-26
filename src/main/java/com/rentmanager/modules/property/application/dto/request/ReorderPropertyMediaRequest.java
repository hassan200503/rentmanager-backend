package com.rentmanager.modules.property.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReorderPropertyMediaRequest(
        @NotNull UUID mediaId,
        int sortOrder
) {
}