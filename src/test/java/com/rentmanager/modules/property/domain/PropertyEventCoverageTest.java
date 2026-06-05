package com.rentmanager.modules.property.domain;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.property.domain.event.*;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PropertyEventCoverageTest {

    // =========================================================
    // CREATED EVENT
    // =========================================================
    @Test
    void shouldEmitCreatedEventCorrectly() {

        Property property = PropertyTestFactory.createProperty();

        List<DomainEvent> events = property.pullDomainEvents();

        PropertyCreatedEvent event = getEvent(events, PropertyCreatedEvent.class);

        assertEquals(property.getTenantId(), event.getTenantId());
        assertEquals(property.getId(), event.getAggregateId());
        assertNotNull(event.getCorrelationId());
        assertNotNull(event.getEventId());
    }

    // =========================================================
    // ACTIVATED EVENT
    // =========================================================
    @Test
    void shouldEmitActivatedEventCorrectly() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");

        List<DomainEvent> events = property.pullDomainEvents();

        PropertyActivatedEvent event = getEvent(events, PropertyActivatedEvent.class);

        assertEquals(property.getTenantId(), event.getTenantId());
        assertEquals(property.getId(), event.getAggregateId());
        assertEquals("corr-1", event.getCorrelationId());
    }

    // =========================================================
    // ARCHIVED EVENT
    // =========================================================
    @Test
    void shouldEmitArchivedEventCorrectly() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");
        property.archive("corr-2");

        List<DomainEvent> events = property.pullDomainEvents();

        PropertyArchivedEvent event = getEvent(events, PropertyArchivedEvent.class);

        assertEquals(property.getTenantId(), event.getTenantId());
        assertEquals(property.getId(), event.getAggregateId());
        assertEquals("corr-2", event.getCorrelationId());
    }

    // =========================================================
    // OCCUPANCY EVENT
    // =========================================================
    @Test
    void shouldEmitOccupancyChangedEventCorrectly() {

        Property property = PropertyTestFactory.createProperty();

        property.markFullyOccupied("corr-1");

        List<DomainEvent> events = property.pullDomainEvents();

        PropertyOccupancyChangedEvent event =
                getEvent(events, PropertyOccupancyChangedEvent.class);

        assertEquals(property.getTenantId(), event.getTenantId());
        assertEquals(property.getId(), event.getAggregateId());
        assertEquals("corr-1", event.getCorrelationId());
    }

    // =========================================================
    // EVENT LIFECYCLE (CLEARING)
    // =========================================================
    @Test
    void shouldClearEventsAfterPull() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");

        List<DomainEvent> firstPull = property.pullDomainEvents();
        List<DomainEvent> secondPull = property.pullDomainEvents();

        assertFalse(firstPull.isEmpty());
        assertTrue(secondPull.isEmpty());
    }

    // =========================================================
    // SAFE EVENT EXTRACTOR (SAAS-GRADE UTILITY)
    // =========================================================
    private <T> T getEvent(List<DomainEvent> events, Class<T> type) {

        return events.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError("Event not found: " + type.getSimpleName())
                );
    }
}