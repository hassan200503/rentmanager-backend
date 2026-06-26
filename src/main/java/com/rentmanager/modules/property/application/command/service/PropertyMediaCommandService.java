package com.rentmanager.modules.property.application.command.service;

import com.rentmanager.modules.property.application.dto.request.ReorderPropertyMediaRequest;

import java.util.List;
import java.util.UUID;

public interface PropertyMediaCommandService {

    void setPrimaryMedia(
            UUID tenantId,
            UUID propertyId,
            UUID mediaId
    );

    void updateCaption(
            UUID tenantId,
            UUID propertyId,
            UUID mediaId,
            String caption
    );




    void reorderMedia(
            UUID tenantId,
            UUID propertyId,
            List<ReorderPropertyMediaRequest> items
    );
}