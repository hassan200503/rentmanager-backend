package com.rentmanager.modules.property.infrastracture.persistence.repository;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
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

    private PropertyJpaEntity persistProperty(PropertyStatus status, String name) {
        PropertyJpaEntity property = new PropertyJpaEntity();
        property.assignTenant(UUID.randomUUID());
        property.setReferenceCode("PROP-" + UUID.randomUUID());
        property.setName(name);
        property.setStatus(status);
        property.setPropertyType(PropertyType.APARTMENT);
        property.setPremisesType(PremisesType.RESIDENTIAL);
        property.setOccupancyStatus(OccupancyStatus.VACANT);
        property.setAddress(PropertyAddressJpaEntity.of(
                "123 Test Street", null, "Nairobi", null, "00100", "Kenya"
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
}