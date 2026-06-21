package com.rentmanager.shared.events;

import com.rentmanager.domain.base.DomainEvent;

import java.util.List;

public interface DomainEventPublisher {
    void publish(DomainEvent event);
    void publishAll(List<DomainEvent> events);
}