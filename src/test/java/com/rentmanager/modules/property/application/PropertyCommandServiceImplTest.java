package com.rentmanager.modules.property.application;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class PropertyCommandServiceImplTest {

    @InjectMocks
    private PropertyCommandServiceImpl service;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyMapper propertyMapper;

    @Mock
    private DomainEventPublisher eventPublisher;

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

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    @Test
    void shouldCreateProperty_underTenantScope_andPersistCorrectly() {

        CreatePropertyRequest request = mock(CreatePropertyRequest.class);

        when(request.getName()).thenReturn("Green Heights");
        when(request.getPropertyType()).thenReturn(PropertyType.WAREHOUSE);
        when(request.getAddress()).thenReturn(mock(Address.class));
        when(request.getGeoLocation()).thenReturn(mock(GeoLocation.class));
        when(request.getDimensions()).thenReturn(mock(PropertyDimensions.class));
        when(request.getDescription()).thenReturn("Nice building");

        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(propertyRepository.save(any(Property.class)))
                .thenReturn(saved);

        // NOTE: `property` (the aggregate that actually has domain events registered
        // on it via Property.create(...)) is a real object built inside the service,
        // not a mock we can stub here. eventPublisher.publishAll(anyList()) will
        // receive whatever real events Property.create() registered — anyList()
        // still matches regardless of contents, so no stub is needed for that path.
        when(propertyMapper.toResponse(saved))
                .thenReturn(response);

        PropertyResponse result = service.createProperty(TENANT_ID, request);

        assertNotNull(result);

        verify(createPropertyValidator).validate(TENANT_ID, request);

        verify(propertyRepository, times(1)).save(any(Property.class));
        verify(eventPublisher).publishAll(anyList());
        verify(propertyMapper).toResponse(saved);
    }

    @Test
    void shouldReturnProperty_whenExistsInTenantScope() {

        Property property = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));

        when(propertyMapper.toResponse(property))
                .thenReturn(response);

        PropertyResponse result = service.getProperty(TENANT_ID, PROPERTY_ID);

        assertNotNull(result);

        verify(propertyRepository)
                .findByIdAndTenantId(PROPERTY_ID, TENANT_ID);

        verify(propertyMapper).toResponse(property);
    }

    @Test
    void shouldThrowException_whenPropertyNotFoundInTenantScope() {

        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.getProperty(TENANT_ID, PROPERTY_ID));

        verify(propertyRepository)
                .findByIdAndTenantId(PROPERTY_ID, TENANT_ID);
    }

    @Test
    void shouldUpdateProperty_throughDomainMutationFlow() {

        UpdatePropertyRequest request = mock(UpdatePropertyRequest.class);

        when(request.getName()).thenReturn("Updated Name");
        when(request.getDescription()).thenReturn("Updated Desc");

        Property existing = mock(Property.class);
        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(existing));

        when(propertyRepository.save(existing))
                .thenReturn(saved);

        when(propertyMapper.toResponse(saved))
                .thenReturn(response);

        PropertyResponse result = service.updateProperty(TENANT_ID, PROPERTY_ID, request);

        assertNotNull(result);

        verify(updatePropertyValidator).validate(TENANT_ID, PROPERTY_ID, request);

        verify(existing).updateDetails("Updated Name", "Updated Desc");

        verify(propertyRepository).save(existing);

        verify(propertyMapper).toResponse(saved);
    }

    @Test
    void shouldActivateProperty_throughValidatorAndDomainFlow() {

        Property property = mock(Property.class);
        Property updated = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(activatePropertyValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(propertyRepository.save(property))
                .thenReturn(updated);

        // Production code pulls events from `property` (the aggregate that had
        // registerEvent() called on it), not from `updated` (the freshly remapped
        // instance returned by the repository). Stub must match.
        when(property.pullDomainEvents())
                .thenReturn(Collections.emptyList());

        when(propertyMapper.toResponse(updated))
                .thenReturn(response);

        PropertyResponse result = service.activateProperty(TENANT_ID, PROPERTY_ID);

        assertNotNull(result);

        verify(activatePropertyValidator)
                .validate(TENANT_ID, PROPERTY_ID);

        verify(property).activate(anyString());

        verify(propertyRepository).save(property);
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    void shouldArchiveProperty_andPersistStateChange() {

        Property property = mock(Property.class);
        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));

        when(propertyRepository.save(property))
                .thenReturn(saved);

        // Production code pulls events from `property`, not `saved`. Stub must match.
        when(property.pullDomainEvents())
                .thenReturn(Collections.emptyList());

        when(propertyMapper.toResponse(saved))
                .thenReturn(response);

        PropertyResponse result = service.archiveProperty(TENANT_ID, PROPERTY_ID);

        assertNotNull(result);

        verify(property).archive(anyString());

        verify(propertyRepository).save(property);
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    void shouldMarkPropertyFullyOccupied() {

        Property property = mock(Property.class);
        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(markFullyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(propertyRepository.save(property))
                .thenReturn(saved);

        // Production code pulls events from `property`, not `saved`. Stub must match.
        when(property.pullDomainEvents())
                .thenReturn(Collections.emptyList());

        when(propertyMapper.toResponse(saved))
                .thenReturn(response);

        PropertyResponse result = service.markFullyOccupied(TENANT_ID, PROPERTY_ID);

        assertNotNull(result);

        verify(property).markFullyOccupied(anyString());
        verify(propertyRepository).save(property);
        verify(eventPublisher).publishAll(anyList());
    }

    @Test
    void shouldMarkPropertyVacant() {

        Property property = mock(Property.class);
        Property saved = mock(Property.class);
        PropertyResponse response = mock(PropertyResponse.class);

        when(propertyMarkVacantValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(propertyRepository.save(property))
                .thenReturn(saved);

        // Production code pulls events from `property`, not `saved`. Stub must match.
        when(property.pullDomainEvents())
                .thenReturn(Collections.emptyList());

        when(propertyMapper.toResponse(saved))
                .thenReturn(response);

        PropertyResponse result = service.markVacant(TENANT_ID, PROPERTY_ID);

        assertNotNull(result);

        verify(property).markVacant(anyString());
        verify(propertyRepository).save(property);
        verify(eventPublisher).publishAll(anyList());
    }
}