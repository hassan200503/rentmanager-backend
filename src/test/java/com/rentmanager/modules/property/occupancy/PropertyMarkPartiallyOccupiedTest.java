package com.rentmanager.modules.property.occupancy;

import com.rentmanager.modules.property.application.command.service.PropertyCommandServiceImpl;
import com.rentmanager.modules.property.application.command.validator.*;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.event.PropertyOccupancyChangedEvent;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dedicated coverage for the markPartiallyOccupied() path.
 *
 * This transition was wired into PropertyCommandServiceImpl/Property as part
 * of the constructor-drift fix (see PropertyLifecycleTest, PropertyIsolationTest,
 * PropertyConcurrencyLifecycleTest), but none of those files exercise it
 * behaviorally — the validator mock was only ever added to satisfy the
 * constructor signature. This file closes that gap.
 */
@ExtendWith(MockitoExtension.class)
class PropertyMarkPartiallyOccupiedTest {

    private PropertyCommandServiceImpl service;

    @Mock private PropertyRepository repository;
    @Mock private PropertyMapper mapper;
    @Mock private DomainEventPublisher eventPublisher;

    @Mock private CreatePropertyValidator createPropertyValidator;
    @Mock private UpdatePropertyValidator updatePropertyValidator;
    @Mock private ActivatePropertyValidator activatePropertyValidator;
    @Mock private MarkFullyOccupiedValidator markFullyOccupiedValidator;
    @Mock private PropertyMarkVacantValidator propertyMarkVacantValidator;
    @Mock private PropertyMarkPartiallyOccupiedValidator propertyMarkPartiallyOccupiedValidator;

    private final UUID TENANT_ID = UUID.randomUUID();
    private final UUID PROPERTY_ID = UUID.randomUUID();

    private Property property;

    @BeforeEach
    void setup() {

        service = new PropertyCommandServiceImpl(
                repository,
                mapper,
                eventPublisher,
                createPropertyValidator,
                updatePropertyValidator,
                activatePropertyValidator,
                markFullyOccupiedValidator,
                propertyMarkVacantValidator,
                propertyMarkPartiallyOccupiedValidator
        );

        // Real aggregate, not a mock — we need actual occupancyStatus
        // transitions and a real domainEvents list for this suite.
        property = Property.create(
                TENANT_ID,
                "Sunset Apartments",
                PropertyType.APARTMENT,
                mock(Address.class),
                mock(GeoLocation.class),
                mock(PropertyDimensions.class),
                "Test property",
                "CORR-INIT"
        );

        // create() itself registers a PropertyCreatedEvent — drain it so
        // each test's event assertions are isolated to markPartiallyOccupied().
        property.pullDomainEvents();
    }

    @Test
    void shouldTransitionToPartiallyOccupied_andPersist() {

        when(propertyMarkPartiallyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(repository.save(property))
                .thenReturn(property);

        when(mapper.toResponse(property))
                .thenReturn(mock(PropertyResponse.class));

        PropertyResponse response = service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID);

        assertNotNull(response);
        assertEquals(OccupancyStatus.PARTIALLY_OCCUPIED, property.getOccupancyStatus());

        verify(propertyMarkPartiallyOccupiedValidator).validate(TENANT_ID, PROPERTY_ID);
        verify(repository).save(property);
    }

    @Test
    void shouldRegisterExactlyOnePropertyOccupancyChangedEvent() {

        when(propertyMarkPartiallyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(repository.save(property))
                .thenReturn(property);

        when(mapper.toResponse(property))
                .thenReturn(mock(PropertyResponse.class));

        service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publishAll(captor.capture());

        List<DomainEvent> events = captor.getValue();
        assertEquals(1, events.size());
        assertInstanceOf(PropertyOccupancyChangedEvent.class, events.get(0));

        // TODO (tighten if desired): once PropertyOccupancyChangedEvent's
        // getters are confirmed, assert previousStatus == VACANT.ordinal()
        // and newStatus == PARTIALLY_OCCUPIED.ordinal() explicitly.
    }

    @Test
    void shouldPullEventsFromOriginalAggregate_notTheSavedInstance() {

        // Regression guard for the exact bug the surrounding methods'
        // comments call out: repository.save() may return a distinct,
        // freshly-remapped instance with its own empty event list. If the
        // service ever pulls events from `saved` instead of `property`,
        // this assertion catches it — publishAll would receive an empty list.
        Property remappedInstance = Property.rehydrate(
                PROPERTY_ID,
                TENANT_ID,
                property.getName(),
                property.getReferenceCode(),
                property.getPropertyType(),
                property.getStatus(),
                OccupancyStatus.PARTIALLY_OCCUPIED,
                property.getAddress(),
                property.getGeoLocation(),
                property.getDimensions(),
                property.getDescription()
        );

        when(propertyMarkPartiallyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(repository.save(property))
                .thenReturn(remappedInstance);

        when(mapper.toResponse(remappedInstance))
                .thenReturn(mock(PropertyResponse.class));

        service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(eventPublisher).publishAll(captor.capture());

        assertEquals(1, captor.getValue().size(),
                "Events must be pulled from the original aggregate, not the repository's returned instance");
    }

    @Test
    void shouldReRegisterEvent_onRepeatedCalls_sinceNoIdempotencyGuardExists() {

        // Unlike activate()/archive(), markPartiallyOccupied() has no
        // `if (status == X) return;` guard. Documenting current behavior,
        // not endorsing it — flips to a legitimate bug the moment product
        // wants "occupy" to be idempotent.
        when(propertyMarkPartiallyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenReturn(property);

        when(repository.save(property))
                .thenReturn(property);

        when(mapper.toResponse(property))
                .thenReturn(mock(PropertyResponse.class));

        service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID);
        service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID);

        verify(eventPublisher, times(2)).publishAll(any());
        assertEquals(OccupancyStatus.PARTIALLY_OCCUPIED, property.getOccupancyStatus());
    }

    @Test
    void shouldPropagateValidatorFailure_withoutMutatingOrPersisting() {

        // Validator type/exception is opaque here (it's a mock); this
        // asserts the service doesn't swallow or wrap failures, and that
        // failure short-circuits before any state change or save.
        RuntimeException validationFailure = new IllegalArgumentException("Property not found");

        when(propertyMarkPartiallyOccupiedValidator.validate(TENANT_ID, PROPERTY_ID))
                .thenThrow(validationFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> service.markPartiallyOccupied(TENANT_ID, PROPERTY_ID));

        assertSame(validationFailure, thrown);
        assertEquals(OccupancyStatus.VACANT, property.getOccupancyStatus());

        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishAll(any());
    }
}