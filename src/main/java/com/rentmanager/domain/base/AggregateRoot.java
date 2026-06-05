package com.rentmanager.domain.base;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AggregateRoot extends BaseTenantEntity {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * Registers a domain event inside the aggregate.
     * Events are later published by the application layer.
     */
    protected void registerEvent(DomainEvent event) {
        if (event == null) return;
        domainEvents.add(event);
    }

    /**
     * Pulls and clears all domain events.
     * Must be called after persistence + before publishing.
     */
    public List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();

        return Collections.unmodifiableList(events);
    }

    /**
     * Utility check for application layer.
     */
    public boolean hasDomainEvents() {
        return !domainEvents.isEmpty();
    }



}