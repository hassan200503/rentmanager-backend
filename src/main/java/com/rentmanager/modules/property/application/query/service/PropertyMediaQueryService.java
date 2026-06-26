package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PropertyMediaResponse;

import java.util.List;
import java.util.UUID;

public interface PropertyMediaQueryService {

    List<PropertyMediaResponse> getPropertyMedia(
            UUID tenantId,
            UUID propertyId
    );
}