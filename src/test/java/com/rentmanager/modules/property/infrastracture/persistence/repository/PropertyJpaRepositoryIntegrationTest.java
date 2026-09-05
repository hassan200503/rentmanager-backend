package com.rentmanager.modules.property.infrastracture.persistence.repository;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-Postgres integration tests for the public-listing-hardening
 * status-filtered queries added to PropertyJpaRepository. Confirms the
 * ACTIVE-only filter actually excludes DRAFT/INACTIVE/UNDER_MAINTENANCE/
 * ARCHIVED properties against a real JPA provider (not a mock).
 *
 * NOTE: @Transactional rolls back each test's writes. Adjust if this
 * project uses a different Testcontainers cleanup convention.
 */
@Transactional
class PropertyJpaRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PropertyJpaRepository propertyJpaRepository;

    @Autowired
    private EntityManager entityManager;

    private PropertyJpaEntity persistProperty(PropertyStatus status, String name) {
        return persistProperty(status, name, "Nairobi");
    }

    private PropertyJpaEntity persistProperty(PropertyStatus status, String name, String city) {
        return persistProperty(status, name, city, "123 Test Street", null, null);
    }

    private PropertyJpaEntity persistProperty(
            PropertyStatus status,
            String name,
            String city,
            String addressLine1,
            String addressLine2,
            String state
    ) {
        PropertyJpaEntity property = new PropertyJpaEntity();
        property.assignTenant(MinimalTenantChainFixture.persistTenant(entityManager));
        property.setReferenceCode("PROP-" + UUID.randomUUID());
        property.setName(name);
        property.setStatus(status);
        property.setPropertyType(PropertyType.APARTMENT);
        property.setPremisesType(PremisesType.RESIDENTIAL);
        property.setOccupancyStatus(OccupancyStatus.VACANT);
        property.setAddress(PropertyAddressJpaEntity.of(
                addressLine1, addressLine2, city, state, "00100", "Kenya"
        ));
        return propertyJpaRepository.saveAndFlush(property);
    }

    // =====================================================
    // findByStatus(status, pageable)
    // =====================================================

    @Test
    void shouldReturnOnlyActiveProperties() {
        persistProperty(PropertyStatus.ACTIVE, "Active One");
        persistProperty(PropertyStatus.DRAFT, "Draft One");
        persistProperty(PropertyStatus.ARCHIVED, "Archived One");
        persistProperty(PropertyStatus.UNDER_MAINTENANCE, "Maintenance One");
        persistProperty(PropertyStatus.INACTIVE, "Inactive One");

        Page<PropertyJpaEntity> result = propertyJpaRepository.findByStatus(
                PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Active One", result.getContent().get(0).getName());
    }

    // =====================================================
    // findByStatusAndNameContainingIgnoreCase
    // =====================================================

    @Test
    void shouldFilterByStatusAndNameKeyword_caseInsensitive() {
        persistProperty(PropertyStatus.ACTIVE, "Green Heights");
        persistProperty(PropertyStatus.DRAFT, "Green Meadows"); // matches name, wrong status
        persistProperty(PropertyStatus.ACTIVE, "Blue Towers");   // right status, wrong name

        Page<PropertyJpaEntity> result = propertyJpaRepository.findByStatusAndNameContainingIgnoreCase(
                PropertyStatus.ACTIVE, "green", PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Green Heights", result.getContent().get(0).getName());
    }

    // =====================================================
    // findByIdAndStatus
    // =====================================================

    @Test
    void shouldFindPropertyById_whenActive() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE, "Findable");

        Optional<PropertyJpaEntity> result = propertyJpaRepository.findByIdAndStatus(
                property.getId(), PropertyStatus.ACTIVE
        );

        assertTrue(result.isPresent());
        assertEquals(property.getId(), result.get().getId());
    }

    @Test
    void shouldNotFindPropertyById_whenDraft() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.DRAFT, "Not Yet Live");

        Optional<PropertyJpaEntity> result = propertyJpaRepository.findByIdAndStatus(
                property.getId(), PropertyStatus.ACTIVE
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotFindPropertyById_whenArchived() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ARCHIVED, "Taken Down");

        Optional<PropertyJpaEntity> result = propertyJpaRepository.findByIdAndStatus(
                property.getId(), PropertyStatus.ACTIVE
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotFindPropertyById_whenIdDoesNotExist() {
        Optional<PropertyJpaEntity> result = propertyJpaRepository.findByIdAndStatus(
                UUID.randomUUID(), PropertyStatus.ACTIVE
        );

        assertTrue(result.isEmpty());
    }

    // =====================================================
    // searchPublic(keyword, location, status, pageable)
    // =====================================================

    @Test
    void shouldMatchKeywordAgainstCity_whenPropertyNameDoesNotMatch() {
        // The landing-page city drill-down used to pass the city as ?q=
        // (keyword); the keyword clause must therefore hit location fields,
        // not just the property name.
        persistProperty(PropertyStatus.ACTIVE, "Sunset Residences", "Mombasa");
        persistProperty(PropertyStatus.ACTIVE, "Harbour Lofts", "Mombasa");
        persistProperty(PropertyStatus.DRAFT, "Nairobi Villas", "Nairobi");

        Page<PropertyJpaEntity> result = propertyJpaRepository.searchPublic(
                "Mombasa", "", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements(),
                "keyword must match the city of ACTIVE properties");
        Page<PropertyJpaEntity> draftOnly = propertyJpaRepository.searchPublic(
                "Nairobi", "", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );
        assertEquals(0, draftOnly.getTotalElements(),
                "DRAFT properties must never leak into public search");
    }

    @Test
    void shouldFilterByLocation_onlyActiveAndCaseInsensitive() {
        persistProperty(PropertyStatus.ACTIVE, "Green Heights", "Nairobi");
        persistProperty(PropertyStatus.ACTIVE, "Sunset Residences", "Mombasa");
        persistProperty(PropertyStatus.ACTIVE, "Blue Towers", "Nairobi West");
        persistProperty(PropertyStatus.DRAFT, "Nairobi Villas", "Nairobi");
        persistProperty(PropertyStatus.ARCHIVED, "Old Nairobi Lofts", "Nairobi");

        Page<PropertyJpaEntity> result = propertyJpaRepository.searchPublic(
                "", "nairobi", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements(),
                "location must match city case-insensitively and exclude non-ACTIVE rows");
    }

    @Test
    void shouldMatchLocationAgainstStreetAndState() {
        persistProperty(PropertyStatus.ACTIVE, "Kilimani Suites", "Nairobi", "Kilimani Road", null, "Nairobi");
        persistProperty(PropertyStatus.ACTIVE, "Lakeside View", "Kisumu", "Oginga Odinga Street", null, "Kisumu");
        persistProperty(PropertyStatus.ACTIVE, "Tulia Gardens", "Thika", null, "Kiambu", null);

        Page<PropertyJpaEntity> byStreet = propertyJpaRepository.searchPublic(
                "", "Kilimani Road", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );
        assertEquals(1, byStreet.getTotalElements());
        assertEquals("Kilimani Suites", byStreet.getContent().get(0).getName());

        Page<PropertyJpaEntity> byState = propertyJpaRepository.searchPublic(
                "", "Kiambu", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );
        assertEquals(1, byState.getTotalElements());
        assertEquals("Tulia Gardens", byState.getContent().get(0).getName());
    }

    @Test
    void shouldCombineKeywordAndLocation_bothMustMatch() {
        persistProperty(PropertyStatus.ACTIVE, "Green Heights", "Nairobi");
        persistProperty(PropertyStatus.ACTIVE, "Green Meadows", "Mombasa");
        persistProperty(PropertyStatus.ACTIVE, "Blue Towers", "Nairobi");

        Page<PropertyJpaEntity> result = propertyJpaRepository.searchPublic(
                "green", "Nairobi", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Green Heights", result.getContent().get(0).getName());
    }

    @Test
    void shouldReturnAllActive_whenBothFiltersNull() {
        persistProperty(PropertyStatus.ACTIVE, "Green Heights", "Nairobi");
        persistProperty(PropertyStatus.ACTIVE, "Sunset Residences", "Mombasa");
        persistProperty(PropertyStatus.DRAFT, "Hidden Draft", "Nairobi");

        Page<PropertyJpaEntity> result = propertyJpaRepository.searchPublic(
                "", "", PropertyStatus.ACTIVE, PageRequest.of(0, 10)
        );

        assertEquals(2, result.getTotalElements(),
                "NULL filters must behave as no-ops, not empty result sets");
    }
}
