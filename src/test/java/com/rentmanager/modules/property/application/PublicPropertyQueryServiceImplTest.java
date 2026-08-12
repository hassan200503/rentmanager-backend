package com.rentmanager.modules.property.application;

import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.application.query.service.PublicPropertyQueryServiceImpl;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Public listing query tests. Follows the project convention: manual
 * mock(Class) construction, no @Mock/@InjectMocks/@Nested, no shared
 * @BeforeEach stubbing — each test creates its own mocks and stubs so
 * Mockito strict stubbing never trips over cross-test reuse.
 */
class PublicPropertyQueryServiceImplTest {

    private final UUID PROPERTY_ID = UUID.randomUUID();

    private PublicPropertyQueryServiceImpl buildService(
            PropertyRepository propertyRepository,
            PropertyMediaRepository propertyMediaRepository,
            PropertyMapper propertyMapper
    ) {
        return new PublicPropertyQueryServiceImpl(
                propertyRepository,
                propertyMediaRepository,
                propertyMapper
        );
    }

    // =====================================================
    // getProperties() — list endpoint
    // =====================================================

    @Test
    void shouldOnlyQueryActiveProperties_whenNoFilters() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable))
                .thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties(null, null, pageable);

        // The unscoped findAll must never be called by the public path.
        verify(propertyRepository, never()).findAll(any(Pageable.class));
        verify(propertyRepository).findByStatus(PropertyStatus.ACTIVE, pageable);
        verify(propertyRepository, never()).searchByStatusAndLocation(
                any(), any(), any(), any()
        );
    }

    @Test
    void shouldOnlyQueryActiveProperties_whenKeywordProvided() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.searchByStatusAndLocation(
                "green", "", PropertyStatus.ACTIVE, pageable
        )).thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties("green", null, pageable);

        // The unscoped search must never be called by the public path.
        verify(propertyRepository, never()).search(anyString(), any(Pageable.class));
        verify(propertyRepository).searchByStatusAndLocation(
                "green", "", PropertyStatus.ACTIVE, pageable
        );
    }

    @Test
    void shouldSearchByLocation_whenLocationProvided() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.searchByStatusAndLocation(
                "", "Nairobi", PropertyStatus.ACTIVE, pageable
        )).thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties(null, "Nairobi", pageable);

        verify(propertyRepository).searchByStatusAndLocation(
                "", "Nairobi", PropertyStatus.ACTIVE, pageable
        );
        // A location-only search must not fall through to the unscoped list.
        verify(propertyRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void shouldCombineKeywordAndLocation_whenBothProvided() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.searchByStatusAndLocation(
                "green", "Mombasa", PropertyStatus.ACTIVE, pageable
        )).thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties("green", "Mombasa", pageable);

        verify(propertyRepository).searchByStatusAndLocation(
                "green", "Mombasa", PropertyStatus.ACTIVE, pageable
        );
    }

    @Test
    void shouldNormalizeBlankFiltersToEmptyString() {
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.searchByStatusAndLocation(
                "", "Nairobi", PropertyStatus.ACTIVE, pageable
        )).thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        // Whitespace keyword + untrimmed location must be trimmed/nulled.
        service.getProperties("   ", "  Nairobi  ", pageable);

        verify(propertyRepository).searchByStatusAndLocation(
                "", "Nairobi", PropertyStatus.ACTIVE, pageable
        );
    }

    @Test
    void shouldExcludeNonActiveProperty_fromPublicListing() {
        // Simulates a DRAFT/INACTIVE/UNDER_MAINTENANCE/ARCHIVED property:
        // the ACTIVE-scoped repository call simply never returns it, so the
        // resulting page is empty. This asserts the service doesn't do any
        // additional in-memory filtering that could mask a broken query.
        PropertyRepository propertyRepository = mock(PropertyRepository.class);
        PropertyMediaRepository propertyMediaRepository = mock(PropertyMediaRepository.class);
        PropertyMapper propertyMapper = mock(PropertyMapper.class);
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable))
                .thenReturn(emptyPage);
        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        Page<PublicPropertyResponse> result = service.getProperties(null, null, pageable);

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
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
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
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
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
        PublicPropertyQueryServiceImpl service = buildService(
                propertyRepository, propertyMediaRepository, propertyMapper
        );

        when(propertyRepository.findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProperty(PROPERTY_ID));

        verify(propertyRepository, never()).findById(PROPERTY_ID);
    }
}
