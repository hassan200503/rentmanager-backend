package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface PublicPropertyQueryService {

    /**
     * Only properties holding at least one publicly visible vacant unit that
     * matches the filters. {@code minRent}/{@code maxRent}/{@code propertyType}
     * apply to the available unit, not the property as a whole.
     */
    Page<PublicPropertyResponse> getProperties(
            String keyword,
            String location,
            BigDecimal minRent,
            BigDecimal maxRent,
            PropertyType propertyType,
            Pageable pageable
    );

    PublicPropertyResponse getProperty(UUID propertyId);
}