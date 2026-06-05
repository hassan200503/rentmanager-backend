package com.rentmanager.modules.lease.domain;

import com.rentmanager.modules.lease.domain.workflow.LeaseEventPublisher;
import com.rentmanager.modules.lease.domain.event.LeaseCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.*;

class LeaseEventPublisherTest {

    @Test
    void shouldDelegateToSpringPublisher() {

        ApplicationEventPublisher springPublisher = mock(ApplicationEventPublisher.class);

        LeaseEventPublisher publisher = new LeaseEventPublisher(springPublisher);

        LeaseCreatedEvent event = mock(LeaseCreatedEvent.class);

        publisher.publish(event);

        verify(springPublisher, times(1)).publishEvent(event);
    }
}