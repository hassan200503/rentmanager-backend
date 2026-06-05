package com.rentmanager.modules.lease.domain.workflow;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class LeaseEventPublisher {

    private final ApplicationEventPublisher publisher;

    public LeaseEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(Object event) {
        publisher.publishEvent(event);
    }
}