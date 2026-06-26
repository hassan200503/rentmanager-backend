package com.rentmanager.modules.unit.application.command.service;

import java.util.List;
import java.util.UUID;

public interface UnitMediaCommandService {

    void setPrimaryMedia(
            UUID tenantId,
            UUID unitId,
            UUID mediaId
    );

    void updateCaption(
            UUID tenantId,
            UUID unitId,
            UUID mediaId,
            String caption
    );

    void reorderMedia(
            UUID tenantId,
            UUID unitId,
            List<UUID> mediaIdsInOrder
    );
}