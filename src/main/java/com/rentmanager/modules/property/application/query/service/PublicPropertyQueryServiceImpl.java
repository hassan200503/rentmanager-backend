package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Blank filters become "" (never null) — see searchPublic() contract. */
    private static String normalize(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    @Override
    public Page<PublicPropertyResponse> getProperties(String keyword, String location, Pageable pageable) {
        // FIX (Public Listings Hardening, 2026-07-08): previously findAll/
        // search with no status filter — a DRAFT/INACTIVE/UNDER_MAINTENANCE/
        // ARCHIVED property was fully visible to the public. Now scoped to
        // PropertyStatus.ACTIVE only.
        //
        // FIX (2026-08-11): the public search ignored location entirely —
        // the frontend sent ?location=Nairobi but Spring silently dropped the
        // unknown param, so "View properties in Nairobi" returned 0 results.
        // keyword now matches name OR location fields; location matches the
        // address/city/street fields. Blank values are normalised to "" so
        // the repository's empty-string guards make each filter a no-op.
        Page<Property> properties =
                (isBlank(keyword) && isBlank(location))
                        ? propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable)
                        : propertyRepository.searchByStatusAndLocation(
                                normalize(keyword),
                                normalize(location),
                                PropertyStatus.ACTIVE,
                                pageable
                        );

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
        // FIX (Public Listings Hardening, 2026-07-08): previously findById
        // with no status filter. Now requires PropertyStatus.ACTIVE; a
        // non-active or nonexistent property both produce the same 404 —
        // deliberately not distinguishing "doesn't exist" from "exists but
        // isn't active," same principle as the M-Pesa callback secret check
        // elsewhere in this codebase (404, not 403, to avoid confirming
        // existence to a prober).
        Property property = propertyRepository.findByIdAndStatus(propertyId, PropertyStatus.ACTIVE)
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