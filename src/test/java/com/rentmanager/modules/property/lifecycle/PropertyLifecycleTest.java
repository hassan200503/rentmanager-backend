package com.rentmanager.modules.property.lifecycle;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyLifecycleTest {

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

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    private Property property;

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

        property = Property.create(
                TENANT_ID,
                "Green Heights",
                PropertyType.BEDSITTER,
                mock(Address.class),
                mock(GeoLocation.class),
                mock(PropertyDimensions.class),
                "Nice property",
                "CORR-1"
        );
    }

    @Test
    void shouldCreateAndActivateProperty_correctLifecycleTransition() {

        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Green Heights");
        request.setPropertyType(PropertyType.BEDSITTER);
        request.setDescription("Nice property");
        request.setAddress(mock(Address.class));
        request.setGeoLocation(mock(GeoLocation.class));
        request.setDimensions(mock(PropertyDimensions.class));

        when(repository.save(any(Property.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(mapper.toResponse(any(Property.class)))
                .thenReturn(mock(PropertyResponse.class));

        // CREATE
        PropertyResponse created = service.createProperty(TENANT_ID, request);

        assertNotNull(created);

        // simulate fetch for activation
        when(activatePropertyValidator.validate(any(), any()))
                .thenReturn(property);

        when(repository.save(property))
                .thenReturn(property);

        service.activateProperty(TENANT_ID, PROPERTY_ID);

        assertEquals(PropertyStatus.ACTIVE, property.getStatus());
    }

    @Test
    void shouldHandleOccupancyLifecycleCorrectly() {

        when(markFullyOccupiedValidator.validate(any(), any()))
                .thenReturn(property);

        when(propertyMarkVacantValidator.validate(any(), any()))
                .thenReturn(property);

        when(repository.save(any(Property.class)))
                .thenReturn(property);

        when(mapper.toResponse(any()))
                .thenReturn(mock(PropertyResponse.class));

        // OCCUPY
        service.markFullyOccupied(TENANT_ID, PROPERTY_ID);

        assertEquals(OccupancyStatus.FULLY_OCCUPIED, property.getOccupancyStatus());

        // VACATE
        service.markVacant(TENANT_ID, PROPERTY_ID);

        assertEquals(OccupancyStatus.VACANT, property.getOccupancyStatus());
    }

    @Test
    void shouldPreserveStateDuringUpdateLifecycle() {

        when(repository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));

        when(repository.save(property))
                .thenReturn(property);

        when(mapper.toResponse(property))
                .thenReturn(mock(PropertyResponse.class));

        service.updateProperty(
                TENANT_ID,
                PROPERTY_ID,
                mock(com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest.class)
        );

        assertNotNull(property.getName());
    }

    @Test
    void shouldArchiveProperty_andLockLifecycle() {

        when(repository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(property));

        when(repository.save(property))
                .thenReturn(property);

        when(mapper.toResponse(property))
                .thenReturn(mock(PropertyResponse.class));

        service.archiveProperty(TENANT_ID, PROPERTY_ID);

        assertEquals(PropertyStatus.ARCHIVED, property.getStatus());
        assertTrue(property.isArchived());
    }
}




