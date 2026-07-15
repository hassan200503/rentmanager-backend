package com.rentmanager.modules.lease.application;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.event.*;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class LeaseEventApplicationTest {

    @Autowired
    private LeaseWorkflowEngine workflowEngine;

    // FIX: added. Workflow engine transitions only call Lease.registerEvent()
    // internally — nothing publishes those events to Spring on its own.
    // In production, LeaseApplicationService.executeAction() is the one place
    // that does lease.pullDomainEvents() + eventPublisher.publishAll(events)
    // after each transition. This test called the workflow engine directly,
    // skipping that flush entirely, so EventCaptureListener never received
    // anything and both assertions failed with events.isEmpty() == true.
    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private EventCaptureListener listener;

    @Test
    void shouldEmitEventsDuringFullActivationFlow() {

        Lease lease = Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-EVT-APP-001",
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(6),
                new BigDecimal("15000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );

        workflowEngine.approve(lease);
        workflowEngine.markAwaitingDeposit(lease);

        workflowEngine.activate(lease);

        // FIX: pull + publish, mirroring LeaseApplicationService.executeAction().
        // Without this, the events registered on `lease` never leave the
        // aggregate and EventCaptureListener stays empty.
        List<DomainEvent> pulled = lease.pullDomainEvents();
        eventPublisher.publishAll(pulled);

        List<Object> events = listener.getEvents();

        assertFalse(events.isEmpty());

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseActivatedEvent)
        );
    }

    @Test
    void shouldEmitTerminationEventThroughApplicationFlow() {

        Lease lease = Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-EVT-APP-002",
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(6),
                new BigDecimal("15000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );

        workflowEngine.approve(lease);
        workflowEngine.markAwaitingDeposit(lease);
        workflowEngine.activate(lease);

        // FIX: flush activation-related events before moving on to
        // termination, so pullDomainEvents() below only contains the
        // termination event (pullDomainEvents() drains the list).
        eventPublisher.publishAll(lease.pullDomainEvents());

        workflowEngine.terminate(lease, TerminationType.TENANT_REQUEST, "exit");

        // FIX: pull + publish the termination event, same as above.
        eventPublisher.publishAll(lease.pullDomainEvents());

        List<Object> events = listener.getEvents();

        assertTrue(
                events.stream().anyMatch(e -> e instanceof LeaseTerminatedEvent)
        );
    }
}

/**
 * SaaS-grade event capture hook
 */
@Component
class EventCaptureListener {

    private final List<Object> events = new ArrayList<>();

    @EventListener
    public void handle(Object event) {
        events.add(event);
    }

    public List<Object> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }
}