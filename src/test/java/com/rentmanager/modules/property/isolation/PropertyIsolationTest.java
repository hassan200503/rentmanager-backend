package com.rentmanager.modules.property.isolation;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyIsolationTest {

    private PropertyCommandServiceImpl service;

    @Mock
    private PropertyRepository repository;

    @Mock
    private PropertyMapper mapper;

    @Mock
    private CreatePropertyValidator createPropertyValidator;

    @Mock
    private UpdatePropertyValidator updatePropertyValidator;

    @Mock
    private ActivatePropertyValidator activatePropertyValidator;

    @Mock
    private MarkFullyOccupiedValidator markFullyOccupiedValidator;

    @Mock
    private PropertyMarkVacantValidator propertyMarkVacantValidator;

    private final UUID TENANT_A = UUID.randomUUID();
    private final UUID TENANT_B = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new PropertyCommandServiceImpl(
                repository,
                mapper,
                createPropertyValidator,
                updatePropertyValidator,
                activatePropertyValidator,
                markFullyOccupiedValidator,
                propertyMarkVacantValidator
        );
    }

    @Test
    void shouldNotAllowCrossTenantAccess_whenTenantMismatch() {

        when(repository.findByIdAndTenantId(PROPERTY_ID, TENANT_B))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.getProperty(TENANT_B, PROPERTY_ID)
        );

        verify(repository).findByIdAndTenantId(PROPERTY_ID, TENANT_B);
    }

    @Test
    void shouldAllowAccessOnlyWithinSameTenant() {

        Property property = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(repository.findByIdAndTenantId(PROPERTY_ID, TENANT_A))
                .thenReturn(Optional.of(property));

        when(mapper.toResponse(property))
                .thenReturn(response);

        PropertyResponse result = service.getProperty(TENANT_A, PROPERTY_ID);

        assertNotNull(result);

        verify(repository).findByIdAndTenantId(PROPERTY_ID, TENANT_A);
    }

    @Test
    void shouldAlwaysPassTenantIdToRepository() {

        Property property = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(repository.findByIdAndTenantId(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.of(property));

        when(mapper.toResponse(property))
                .thenReturn(response);

        service.getProperty(TENANT_A, PROPERTY_ID);

        verify(repository, times(1))
                .findByIdAndTenantId(PROPERTY_ID, TENANT_A);

        verifyNoMoreInteractions(repository);
    }

    @Test
    void shouldIsolateTenantOnCreateOperation() {

        CreatePropertyRequest request = mock(CreatePropertyRequest.class);

        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(repository.save(any(Property.class)))
                .thenReturn(saved);

        when(mapper.toResponse(saved))
                .thenReturn(response);

        service.createProperty(TENANT_A, request);

        verify(repository).save(any(Property.class));
    }

    @Test
    void shouldRejectUpdateWhenTenantMismatch() {

        UUID tenantB = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        UpdatePropertyRequest request = mock(UpdatePropertyRequest.class);

        when(repository.findByIdAndTenantId(propertyId, tenantB))
                .thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateProperty(tenantB, propertyId, request)
        );

        assertEquals("Property not found", ex.getMessage());

        verify(repository).findByIdAndTenantId(propertyId, tenantB);
        verifyNoMoreInteractions(repository);
    }
}