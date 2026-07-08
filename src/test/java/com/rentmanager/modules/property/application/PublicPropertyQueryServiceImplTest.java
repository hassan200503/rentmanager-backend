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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicPropertyQueryServiceImplTest {

    @InjectMocks
    private PublicPropertyQueryServiceImpl service;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyMediaRepository propertyMediaRepository;

    @Mock
    private PropertyMapper propertyMapper;

    private final UUID PROPERTY_ID = UUID.randomUUID();

    // =====================================================
    // getProperties() — list endpoint
    // =====================================================

    @Test
    void shouldOnlyQueryActiveProperties_whenNoKeyword() {

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable))
                .thenReturn(emptyPage);

        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties(null, pageable);

        // The unscoped findAll must never be called by the public path.
        verify(propertyRepository, never()).findAll(any(Pageable.class));
        verify(propertyRepository).findByStatus(PropertyStatus.ACTIVE, pageable);
    }

    @Test
    void shouldOnlyQueryActiveProperties_whenKeywordProvided() {

        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.searchByStatus("green", PropertyStatus.ACTIVE, pageable))
                .thenReturn(emptyPage);

        when(propertyMediaRepository.findAllByPropertyIdIn(anyList()))
                .thenReturn(List.of());

        service.getProperties("green", pageable);

        // The unscoped search must never be called by the public path.
        verify(propertyRepository, never()).search(anyString(), any(Pageable.class));
        verify(propertyRepository).searchByStatus("green", PropertyStatus.ACTIVE, pageable);
    }

    @Test
    void shouldExcludeNonActiveProperty_fromPublicListing() {
        // Simulates a DRAFT/INACTIVE/UNDER_MAINTENANCE/ARCHIVED property:
        // the ACTIVE-scoped repository call simply never returns it, so the
        // resulting page is empty. This asserts the service doesn't do any
        // additional in-memory filtering that could mask a broken query.
        Pageable pageable = mock(Pageable.class);
        Page<Property> emptyPage = new PageImpl<>(List.of());

        when(propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable))
                .thenReturn(emptyPage);

        Page<PublicPropertyResponse> result = service.getProperties(null, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    // =====================================================
    // getProperty() — detail endpoint
    // =====================================================

    @Test
    void shouldReturnProperty_whenActive() {

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
        when(propertyRepository.findByIdAndStatus(PROPERTY_ID, PropertyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getProperty(PROPERTY_ID));

        verify(propertyRepository, never()).findById(PROPERTY_ID);
    }
}