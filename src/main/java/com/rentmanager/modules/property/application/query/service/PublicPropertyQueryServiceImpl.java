package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicPropertyQueryServiceImpl implements PublicPropertyQueryService {

    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final PropertyMapper propertyMapper;

    @Override
    public Page<PublicPropertyResponse> getProperties(String keyword, Pageable pageable) {
        Page<Property> properties = (keyword == null || keyword.isBlank())
                ? propertyRepository.findAll(pageable)
                : propertyRepository.search(keyword, pageable);

        List<UUID> propertyIds = properties.getContent().stream()
                .map(Property::getId)
                .toList();

        // one query for the whole page, not one per property
        Map<UUID, List<String>> imagesByPropertyId = propertyMediaRepository
                .findAllByPropertyIdIn(propertyIds)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.groupingBy(
                        PropertyMedia::getPropertyId,
                        Collectors.mapping(PropertyMedia::getFileUrl, Collectors.toList())
                ));

        return properties.map(property -> {
            PublicPropertyResponse response = propertyMapper.toPublicResponse(property);
            List<String> images = imagesByPropertyId.getOrDefault(property.getId(), List.of());

            return PublicPropertyResponse.builder()
                    .propertyId(response.getPropertyId())
                    .name(response.getName())
                    .propertyType(response.getPropertyType())
                    .address(response.getAddress())
                    .geoLocation(response.getGeoLocation())
                    .description(response.getDescription())
                    .images(images)
                    .build();
        });
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

        PublicPropertyResponse response = propertyMapper.toPublicResponse(property);

        List<String> images = propertyMediaRepository
                .findAllByPropertyId(propertyId)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(PropertyMedia::getFileUrl)
                .toList();

        return PublicPropertyResponse.builder()
                .propertyId(response.getPropertyId())
                .name(response.getName())
                .propertyType(response.getPropertyType())
                .address(response.getAddress())
                .geoLocation(response.getGeoLocation())
                .description(response.getDescription())
                .images(images)
                .build();
    }
}