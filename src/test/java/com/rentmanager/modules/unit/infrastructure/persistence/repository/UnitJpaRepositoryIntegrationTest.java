package com.rentmanager.modules.unit.infrastructure.persistence.repository;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAddressJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-Postgres integration tests for the public-listing-hardening JOIN
 * queries in UnitJpaRepository. These deliberately do NOT mock the
 * repository, because the thing under test is whether the ad-hoc
 * "JOIN PropertyJpaEntity p ON u.propertyId = p.id" JPQL actually filters
 * correctly against a real JPA provider — the exact class of thing that
 * broke silently under Hibernate 6.4's stricter enum binding (see handoff
 * doc §2.4). A Mockito-based test can't catch that; this one can.
 *
 * NOTE: @Transactional is used to roll back each test's writes so tests
 * don't interfere with each other on the shared Postgres container. Adjust
 * if this project uses a different cleanup convention for Testcontainers
 * tests.
 */
@Transactional
class UnitJpaRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UnitJpaRepository unitJpaRepository;

    @Autowired
    private PropertyJpaRepository propertyJpaRepository;

    private PropertyJpaEntity persistProperty(PropertyStatus status) {
        PropertyJpaEntity property = new PropertyJpaEntity();
        property.assignTenant(UUID.randomUUID());
        property.setReferenceCode("PROP-" + UUID.randomUUID());
        property.setName("Test Property");
        property.setStatus(status);
        property.setPropertyType(PropertyType.APARTMENT);
        property.setPremisesType(PremisesType.RESIDENTIAL);
        property.setOccupancyStatus(OccupancyStatus.VACANT);
        property.setAddress(PropertyAddressJpaEntity.of(
                "123 Test Street", null, "Nairobi", null, "00100", "Kenya"
        ));
        return propertyJpaRepository.saveAndFlush(property);
    }

    private UnitJpaEntity persistUnit(UUID propertyId, UnitStatus unitStatus, UnitOccupancyStatus occupancyStatus) {
        UnitJpaEntity unit = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .propertyId(propertyId)
                .unitNumber("U-" + UUID.randomUUID())
                .label("Test Unit")
                .status(unitStatus)
                .occupancyStatus(occupancyStatus)
                .rentAmount(BigDecimal.valueOf(1000))
                .description("A test unit")
                .vacatedAt(occupancyStatus == UnitOccupancyStatus.VACANT ? LocalDateTime.now().minusDays(1) : null)
                .build();
        return unitJpaRepository.saveAndFlush(unit);
    }

    // =====================================================
    // searchPubliclyVisible
    // =====================================================

    @Test
    void shouldReturnUnit_whenUnitActive_andOccupancyVacant_andPropertyActive() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldExcludeUnit_whenUnitIsActiveAndVacant_butParentPropertyIsDraft() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.DRAFT);
        persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeUnit_whenUnitIsActiveAndVacant_butParentPropertyIsArchived() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ARCHIVED);
        persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeUnit_whenPropertyIsActive_butUnitStatusIsInactive() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(property.getId(), UnitStatus.INACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeUnit_whenPropertyIsActive_butUnitStatusIsMaintenance() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(property.getId(), UnitStatus.MAINTENANCE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeUnit_whenPropertyIsActive_butUnitStatusIsArchived() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(property.getId(), UnitStatus.ARCHIVED, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldExcludeUnit_whenUnitAndPropertyBothActive_butOccupancyIsOccupied() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.OCCUPIED);

        Page<UnitJpaEntity> result = unitJpaRepository.searchPubliclyVisible(
                null, UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void shouldRespectKeywordFilter_onPubliclyVisibleUnits() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        UnitJpaEntity unit = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .propertyId(property.getId())
                .unitNumber("A-101")
                .label("Penthouse Suite")
                .status(UnitStatus.ACTIVE)
                .occupancyStatus(UnitOccupancyStatus.VACANT)
                .rentAmount(BigDecimal.valueOf(5000))
                .description("Spacious rooftop unit")
                .vacatedAt(LocalDateTime.now().minusDays(1))
                .build();
        unitJpaRepository.saveAndFlush(unit);

        Page<UnitJpaEntity> matching = unitJpaRepository.searchPubliclyVisible(
                "rooftop", UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );
        Page<UnitJpaEntity> nonMatching = unitJpaRepository.searchPubliclyVisible(
                "basement", UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertEquals(1, matching.getTotalElements());
        assertTrue(nonMatching.getContent().isEmpty());
    }

    // =====================================================
    // findPubliclyVisibleByProperty
    // =====================================================

    @Test
    void shouldReturnUnitsForProperty_onlyWhenBothUnitAndPropertyActive() {
        PropertyJpaEntity activeProperty = persistProperty(PropertyStatus.ACTIVE);
        persistUnit(activeProperty.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        PropertyJpaEntity draftProperty = persistProperty(PropertyStatus.DRAFT);
        persistUnit(draftProperty.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> resultForActive = unitJpaRepository.findPubliclyVisibleByProperty(
                activeProperty.getId(), UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );
        Page<UnitJpaEntity> resultForDraft = unitJpaRepository.findPubliclyVisibleByProperty(
                draftProperty.getId(), UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 10)
        );

        assertEquals(1, resultForActive.getTotalElements());
        assertTrue(resultForDraft.getContent().isEmpty());
    }

    // =====================================================
    // findPubliclyVisibleById
    // =====================================================

    @Test
    void shouldFindUnitById_whenPubliclyVisible() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);
        UnitJpaEntity unit = persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Optional<UnitJpaEntity> result = unitJpaRepository.findPubliclyVisibleById(
                unit.getId(), UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE
        );

        assertTrue(result.isPresent());
        assertEquals(unit.getId(), result.get().getId());
    }

    @Test
    void shouldNotFindUnitById_whenParentPropertyIsNotActive() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.UNDER_MAINTENANCE);
        UnitJpaEntity unit = persistUnit(property.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Optional<UnitJpaEntity> result = unitJpaRepository.findPubliclyVisibleById(
                unit.getId(), UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldNotFindUnitById_whenUnitDoesNotExist() {
        Optional<UnitJpaEntity> result = unitJpaRepository.findPubliclyVisibleById(
                UUID.randomUUID(), UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE
        );

        assertTrue(result.isEmpty());
    }

    // =====================================================
    // findLongestVacantPubliclyVisible
    // =====================================================

    @Test
    void shouldReturnLongestVacantPubliclyVisibleUnit_orderedByVacatedAt() {
        PropertyJpaEntity property = persistProperty(PropertyStatus.ACTIVE);

        UnitJpaEntity recentlyVacated = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .propertyId(property.getId())
                .unitNumber("U-" + UUID.randomUUID())
                .status(UnitStatus.ACTIVE)
                .occupancyStatus(UnitOccupancyStatus.VACANT)
                .rentAmount(BigDecimal.valueOf(1000))
                .vacatedAt(LocalDateTime.now().minusDays(1))
                .build();
        unitJpaRepository.saveAndFlush(recentlyVacated);

        UnitJpaEntity longVacated = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .propertyId(property.getId())
                .unitNumber("U-" + UUID.randomUUID())
                .status(UnitStatus.ACTIVE)
                .occupancyStatus(UnitOccupancyStatus.VACANT)
                .rentAmount(BigDecimal.valueOf(1000))
                .vacatedAt(LocalDateTime.now().minusDays(90))
                .build();
        unitJpaRepository.saveAndFlush(longVacated);

        Page<UnitJpaEntity> result = unitJpaRepository.findLongestVacantPubliclyVisible(
                UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 1)
        );

        assertEquals(1, result.getContent().size());
        assertEquals(longVacated.getId(), result.getContent().get(0).getId());
    }

    @Test
    void shouldExcludeArchivedPropertyUnit_fromLongestVacant_evenIfLongestVacated() {
        PropertyJpaEntity archivedProperty = persistProperty(PropertyStatus.ARCHIVED);
        UnitJpaEntity longVacatedButArchived = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .propertyId(archivedProperty.getId())
                .unitNumber("U-" + UUID.randomUUID())
                .status(UnitStatus.ACTIVE)
                .occupancyStatus(UnitOccupancyStatus.VACANT)
                .rentAmount(BigDecimal.valueOf(1000))
                .vacatedAt(LocalDateTime.now().minusYears(1))
                .build();
        unitJpaRepository.saveAndFlush(longVacatedButArchived);

        PropertyJpaEntity activeProperty = persistProperty(PropertyStatus.ACTIVE);
        UnitJpaEntity recentButVisible = persistUnit(activeProperty.getId(), UnitStatus.ACTIVE, UnitOccupancyStatus.VACANT);

        Page<UnitJpaEntity> result = unitJpaRepository.findLongestVacantPubliclyVisible(
                UnitOccupancyStatus.VACANT, UnitStatus.ACTIVE, PropertyStatus.ACTIVE,
                PageRequest.of(0, 1)
        );

        assertEquals(1, result.getContent().size());
        assertEquals(recentButVisible.getId(), result.getContent().get(0).getId());
    }
}