package com.rentmanager.shared.dto;

import java.util.UUID;

public record MediaUploadResponse(
        UUID id,
        UUID tenantId,
        UUID resourceId,      // propertyId or unitId
        String url,
        String type,
        String caption,
        boolean primary,
        int sortOrder
) {}