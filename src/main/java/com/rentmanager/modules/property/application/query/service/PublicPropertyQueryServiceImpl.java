package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicPropertyQueryServiceImpl implements PublicPropertyQueryService {

    private final PropertyRepository propertyRepository;
    private final PropertyMapper propertyMapper;

    @Override
    public Page<PublicPropertyResponse> getProperties(String keyword, Pageable pageable) {
        Page<Property> properties = (keyword == null || keyword.isBlank())
                ? propertyRepository.findAll(pageable)
                : propertyRepository.search(keyword, pageable);

        return properties.map(propertyMapper::toPublicResponse);
    }

    @Override
    public PublicPropertyResponse getProperty(UUID propertyId) {

        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Property not found",
                                ErrorCode.PROPERTY_NOT_FOUND
                        )
                );

        return propertyMapper.toPublicResponse(property);
    }
}