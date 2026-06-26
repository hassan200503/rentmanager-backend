package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.shared.dto.MediaUploadResponse;

import java.util.List;
import java.util.UUID;

public interface UnitMediaQueryService {

    List<MediaUploadResponse> getUnitMedia(
            UUID tenantId,
            UUID unitId
    );
}