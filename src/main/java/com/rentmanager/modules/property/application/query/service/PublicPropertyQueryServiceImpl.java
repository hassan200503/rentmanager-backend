package com.rentmanager.modules.property.application.query.service;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.port.PublicVacancyPort;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicPropertyQueryServiceImpl implements PublicPropertyQueryService {

    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final PropertyMapper propertyMapper;
    private final PublicVacancyPort publicVacancyPort;

    @Override
    public Page<PublicPropertyResponse> getProperties(
            String keyword,
            String location,
            BigDecimal minRent,
            BigDecimal maxRent,
            PropertyType propertyType,
            Pageable pageable) {

        // FIX (Public Listings Hardening, 2026-07-08): previously findAll/
        // search with no status filter — a DRAFT/INACTIVE/UNDER_MAINTENANCE/
        // ARCHIVED property was fully visible to the public. Now scoped to
        // PropertyStatus.ACTIVE only.
        //
        // FIX (2026-08-11): the public search ignored location entirely —
        // the frontend sent ?location=Nairobi but Spring silently dropped the
        // unknown param, so "View properties in Nairobi" returned 0 results.
        //
        // FIX (2026-09-03): the page listed every ACTIVE property whether or
        // not anything in it was available to rent, under a heading reading
        // "Available Properties" and a promise of "real vacancies, not stale
        // listings". A renter could work through a page of cards and find
        // nothing rentable behind any of them, and the landlord paying for
        // that listing never learned why nobody called. Selection now starts
        // from vacancy, and price/type filters are applied to the available
        // unit rather than to the property, because "under 30,000" is a
        // question about the home a renter would move into.
        Page<UUID> propertyIds = publicVacancyPort.findPropertyIdsWithVacancy(
                keyword, location, minRent, maxRent, propertyType, pageable);

        List<UUID> ids = propertyIds.getContent();
        if (ids.isEmpty()) {
            return propertyIds.map(id -> null);
        }

        // The id page is ordered by property name; loading by id set loses
        // that order, so it is reimposed below rather than left to whatever
        // Postgres returns.
        Map<UUID, Property> propertiesById = propertyRepository
                .findAllByIdInAndStatus(ids, PropertyStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(Property::getId, p -> p));

        // one query for the whole page, not one per property
        Map<UUID, List<String>> imagesByPropertyId = propertyMediaRepository
                .findAllByPropertyIdIn(ids)
                .stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .collect(Collectors.groupingBy(
                        PropertyMedia::getPropertyId,
                        Collectors.mapping(PropertyMedia::getFileUrl, Collectors.toList())
                ));

        Map<UUID, PublicVacancyPort.VacancySummary> vacancyByPropertyId =
                publicVacancyPort.summariseVacancy(ids);

        List<PublicPropertyResponse> content = ids.stream()
                .map(propertiesById::get)
                // A property selected by the vacancy query but missing here
                // would mean it stopped being ACTIVE between the two queries.
                // Dropping it is right: it is no longer public.
                .filter(Objects::nonNull)
                .map(property -> toResponse(
                        property,
                        imagesByPropertyId.getOrDefault(property.getId(), List.of()),
                        vacancyByPropertyId.get(property.getId())))
                .toList();

        return new PageImpl<>(content, pageable, propertyIds.getTotalElements());
    }

    private PublicPropertyResponse toResponse(
            Property property,
            List<String> images,
            PublicVacancyPort.VacancySummary vacancy) {

        PublicPropertyResponse mapped = propertyMapper.toPublicResponse(property);

        return PublicPropertyResponse.builder()
                .propertyId(mapped.getPropertyId())
                .name(mapped.getName())
                .propertyType(mapped.getPropertyType())
                .address(mapped.getAddress())
                .geoLocation(mapped.getGeoLocation())
                .description(mapped.getDescription())
                .images(images)
                // Null rather than zero when nothing is available, so a card
                // renders nothing instead of quoting a rent of KES 0.
                .availableUnits(vacancy == null ? null : vacancy.availableUnits())
                .minRent(vacancy == null ? null : vacancy.minRent())
                .maxRent(vacancy == null ? null : vacancy.maxRent())
                .build();
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