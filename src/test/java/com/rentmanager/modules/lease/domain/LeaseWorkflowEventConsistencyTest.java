package com.rentmanager.modules.lease.domain;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.workflow.*;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.*;

class LeaseWorkflowEventConsistencyTest {

    @Test
    void shouldPublishEventOnActivationFlow() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);

        LeaseWorkflowEngine engine = new LeaseWorkflowEngine(
                validator, publisher, repo, domainService, occupancyService
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
        engine.activate(lease);

        verify(publisher, times(1)).publish(any());
    }
}