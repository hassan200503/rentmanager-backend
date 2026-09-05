package com.rentmanager.modules.property.application;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.application.port.PublicVacancyPort;
import com.rentmanager.modules.property.application.query.service.PublicPropertyQueryServiceImpl;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Public listing query tests. Follows the project convention: manual
 * mock(Class) construction, no @Mock/@InjectMocks/@Nested, no shared
 * @BeforeEach stubbing — each test creates its own mocks and stubs so
 * Mockito strict stubbing never trips over cross-test reuse.
 *
 * <p>Selection now starts from {@link PublicVacancyPort}: a property is only
 * public here when it has a matching vacant unit (2026-09-03 fix for the
 * "Available Properties" page listing fully-occupied properties).
 */
class PublicPropertyQueryServiceImplTest {

    private final UUID PROPERTY_ID = UUID.randomUUID();

    private PublicPropertyQueryServiceImpl buildService(
            PropertyRepository propertyRepository,
            PropertyMediaRepository propertyMediaRepository,
            PropertyMapper propertyMapper,
            PublicVacancyPort publicVacancyPort
    ) {
        return new PublicPropertyQueryServiceImpl(
                propertyRepository,
                propertyMediaRepository,
                propertyMapper,
                publicVacancyPort
        );
    }

    // =====================================================
    // getProperties() — list endpoint
    // =====================================================

    @Test
    void shouldReturnEmptyPage_whenNoPropertyHasVacancy() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        Pageable pageable = mock(Pageable.class);

        when(publicVacancyPort.findPropertyIdsWithVacancy(
                null, null, null, null, null, pageable
        )).thenReturn(new PageImpl<>(List.of()));

        Page<PublicPropertyResponse> result =
                service.getProperties(null, null, null, null, null, pageable);

        assertTrue(result.getContent().isEmpty());
        // No point loading properties, media or vacancy summaries for an
        // empty id set — and an empty IN () would be a Postgres syntax error.
        verify(propertyRepository, never()).findAllByIdInAndStatus(anyList(), any());
        verify(publicVacancyPort, never()).summariseVacancy(anyList());
    }

    @Test
    void shouldPassEveryFilterThroughToTheVacancyPort() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        Pageable pageable = mock(Pageable.class);
        BigDecimal min = new BigDecimal("10000");
        BigDecimal max = new BigDecimal("50000");

        when(publicVacancyPort.findPropertyIdsWithVacancy(
                "green", "Nairobi", min, max, PropertyType.APARTMENT, pageable
        )).thenReturn(new PageImpl<>(List.of()));

        service.getProperties("green", "Nairobi", min, max, PropertyType.APARTMENT, pageable);

        verify(publicVacancyPort).findPropertyIdsWithVacancy(
                "green", "Nairobi", min, max, PropertyType.APARTMENT, pageable
        );
    }

    @Test
    void shouldAttachVacancySummaryAndImages_toEachMatchingProperty() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        Pageable pageable = mock(Pageable.class);
        Property property = mock(Property.class);
        when(property.getId()).thenReturn(PROPERTY_ID);

        PublicPropertyResponse mapped = PublicPropertyResponse.builder()
                .propertyId(PROPERTY_ID)
                .name("Green Heights")
                .build();

        when(publicVacancyPort.findPropertyIdsWithVacancy(
                null, null, null, null, null, pageable
        )).thenReturn(new PageImpl<>(List.of(PROPERTY_ID)));
        when(propertyRepository.findAllByIdInAndStatus(List.of(PROPERTY_ID), PropertyStatus.ACTIVE))
                .thenReturn(List.of(property));
        when(propertyMediaRepository.findAllByPropertyIdIn(List.of(PROPERTY_ID)))
                .thenReturn(List.of());
        when(propertyMapper.toPublicResponse(property)).thenReturn(mapped);
        when(publicVacancyPort.summariseVacancy(List.of(PROPERTY_ID)))
                .thenReturn(Map.of(PROPERTY_ID,
                        new PublicVacancyPort.VacancySummary(2, new BigDecimal("15000"), new BigDecimal("22000"))));

        Page<PublicPropertyResponse> result =
                service.getProperties(null, null, null, null, null, pageable);

        assertEquals(1, result.getContent().size());
        PublicPropertyResponse response = result.getContent().get(0);
        assertEquals(2, response.getAvailableUnits());
        assertEquals(new BigDecimal("15000"), response.getMinRent());
        assertEquals(new BigDecimal("22000"), response.getMaxRent());
    }

    @Test
    void shouldDropAPropertyThatStoppedBeingActive_betweenTheTwoQueries() {
        // The vacancy port selected this id, but the ACTIVE-scoped bulk load
        // no longer returns it — must be filtered, not surfaced as a null card.
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        Pageable pageable = mock(Pageable.class);

        when(publicVacancyPort.findPropertyIdsWithVacancy(
                null, null, null, null, null, pageable
        )).thenReturn(new PageImpl<>(List.of(PROPERTY_ID)));
        when(propertyRepository.findAllByIdInAndStatus(List.of(PROPERTY_ID), PropertyStatus.ACTIVE))
                .thenReturn(List.of());
        when(propertyMediaRepository.findAllByPropertyIdIn(List.of(PROPERTY_ID)))
                .thenReturn(List.of());
        when(publicVacancyPort.summariseVacancy(List.of(PROPERTY_ID)))
                .thenReturn(Map.of());

        Page<PublicPropertyResponse> result =
                service.getProperties(null, null, null, null, null, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    // =====================================================
    // getProperty() — detail endpoint
    // =====================================================

    @Test
    void shouldReturnProperty_whenActive() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        Property property = mock(Property.class);
        PublicPropertyResponse mapped = PublicPropertyResponse.builder()
                .propertyId(PROPERTY_ID)
                .name("Green Heights")
                .build();

        when(propertyRepository.findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE))
                .thenReturn(Optional.of(property));
        when(propertyMapper.toPublicResponse(property)).thenReturn(mapped);
        when(propertyMediaRepository.findAllByPropertyId(PROPERTY_ID))
                .thenReturn(List.of());

        PublicPropertyResponse result = service.getProperty(PROPERTY_ID);

        assertNotNull(result);
        assertEquals(PROPERTY_ID, result.getPropertyId());
        verify(propertyRepository).findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE);
        verify(propertyRepository, never()).findById(PROPERTY_ID);
    }

    @Test
    void shouldThrow404_whenPropertyDoesNotExist() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        when(propertyRepository.findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProperty(PROPERTY_ID));
    }

    @Test
    void shouldThrow404_whenPropertyExistsButIsNotActive() {
        // Same exception/error path as "doesn't exist" — the ACTIVE-scoped
        // query call simply returns empty for a DRAFT/INACTIVE/ARCHIVED
        // property, so the service can't distinguish (and must not try to
        // distinguish) the two cases. This is the deliberate 404-not-403
        // behavior from the handoff doc.
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicVacancyPort publicVacancyPort = mock(PublicVacancyPort.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper, publicVacancyPort
        );

        when(propertyRepository.findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProperty(PROPERTY_ID));

        verify(propertyRepository, never()).findById(PROPERTY_ID);
    }
}
