package com.rentmanager.modules.lease.domain;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.workflow.*;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LeaseWorkflowEventConsistencyTest {

    @Test
    void shouldRegisterExactlyOneEventOnActivationFlow() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);

        LeaseWorkflowEngine engine = new LeaseWorkflowEngine(
                validator, repo, domainService, occupancyService
        );

        Lease lease = Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-EVT-100",
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(6),
                new BigDecimal("10000"),
                new BigDecimal("20000"),
                new BigDecimal("500"),
                7,
                false
        );

        lease.approve();
        lease.markAwaitingDeposit();

        // Drain any events registered by approve()/markAwaitingDeposit() so
        // this test isolates activation's contribution only.
        lease.pullDomainEvents();

        engine.activate(lease);

        // The single-publish contract: activation registers exactly one
        // domain event on the aggregate, with no direct publisher call
        // anywhere in the engine. This replaces the old
        // verify(publisher, times(1)).publish(any()) assertion, which
        // tested the double-publish path we removed.
        List<DomainEvent> events = lease.pullDomainEvents();
        assertThat(events).hasSize(1);

// TODO (tighten if desired): assert the concrete type, e.g.
// assertThat(events.get(0)).isInstanceOf(LeaseActivatedEvent.class);
        // TODO (tighten if desired): assert the concrete type, e.g.
        // assertThat(events.get(0)).isInstanceOf(LeaseActivatedEvent.class);
    }
}