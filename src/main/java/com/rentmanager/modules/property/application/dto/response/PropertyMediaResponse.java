package com.rentmanager.modules.property.application.dto.response;

import java.util.UUID;

public record PropertyMediaResponse(
        UUID id,
        UUID propertyId,
        String fileUrl,
        String fileName,
        String mediaType,
        boolean primaryMedia,
        String caption,
        int sortOrder
) {
}