package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PublicPropertyQueryService {

    Page<PublicPropertyResponse> getProperties(
            String keyword,
            String location,
            Pageable pageable
    );

    PublicPropertyResponse getProperty(UUID propertyId);
}