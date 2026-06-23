package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.service.LeaseDomainService;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.workflow.LeaseEventPublisher;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaseBusinessConstraintTest {

    private LeaseWorkflowEngine engine(
            LeaseWorkflowValidator validator,
            LeaseEventPublisher publisher,
            LeaseRepository repo,
            LeaseDomainService domainService,
            UnitOccupancyService occupancyService
    ) {
        return new LeaseWorkflowEngine(
                validator,
                publisher,
                repo,
                domainService,
                occupancyService
        );
    }

    private Lease createLease() {
        return Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-" + System.nanoTime(),
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(12),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );
    }

    private Lease createActiveLease() {
        Lease lease = createLease();
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        return lease;
    }

    @Test
    void shouldActivateLeaseSuccessfullyWhenUnitIsAvailable() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);

        doNothing().when(occupancyService).validateUnitAvailability(any());

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        lease.approve();
        lease.markAwaitingDeposit();
        engine.activate(lease);

        assertEquals(LeaseStatus.ACTIVE, lease.getStatus());
        verify(publisher, atLeastOnce()).publish(any());
    }

    @Test
    void shouldBlockActivationWhenUnitNotAvailable() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);

        doThrow(new IllegalStateException("Unit already occupied"))
                .when(occupancyService)
                .validateUnitAvailability(any());

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        assertThrows(
                IllegalStateException.class,
                () -> engine.activate(lease)
        );
    }

    @Test
    void shouldApproveLease() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createLease();

        engine.approve(lease);

        assertEquals(LeaseStatus.PENDING_APPROVAL, lease.getStatus());
    }

    @Test
    void shouldRejectLease() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createLease();

        engine.reject(lease, "invalid docs");

        assertEquals(LeaseStatus.TERMINATED, lease.getStatus());
    }

    @Test
    void shouldTerminateLease() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createActiveLease();

        engine.terminate(lease, TerminationType.TENANT_REQUEST, "end of contract");

        assertEquals(LeaseStatus.TERMINATED, lease.getStatus());
    }

    @Test
    void shouldRenewLease() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createActiveLease();

        engine.renew(
                lease,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(12)
        );

        assertEquals(LeaseStatus.RENEWED, lease.getStatus());
    }

    @Test
    void shouldBlockActivationWhenDateOverlapDetected() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);

        doThrow(new IllegalStateException("Date overlap detected"))
                .when(validator).validateActivation(any());

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        assertThrows(
                IllegalStateException.class,
                () -> engine.activate(lease)
        );

        verify(validator).validateActivation(lease);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldBlockActivationWhenTenantIsolationFails() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);

        doThrow(new IllegalStateException("Tenant isolation violation"))
                .when(validator).validateActivation(any());

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        assertThrows(
                IllegalStateException.class,
                () -> engine.activate(lease)
        );

        verify(validator).validateActivation(lease);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldBlockActivationWhenFinancialLockExists() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);

        doThrow(new IllegalStateException("Financial lock active"))
                .when(validator).validateActivation(any());

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        assertThrows(
                IllegalStateException.class,
                () -> engine.activate(lease)
        );

        verify(validator).validateActivation(lease);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldExpireLeaseSuccessfully() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createActiveLease();

        engine.expire(lease);

        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());
    }

    @Test
    void shouldPublishEventOnExpiration() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createActiveLease();

        engine.expire(lease);

        verify(publisher, atLeastOnce()).publish(any());
    }

    @Test
    void shouldRejectCancellationForActiveLease() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createActiveLease();

        assertThrows(
                IllegalStateException.class,
                () -> engine.reject(lease, "invalid cancel attempt")
        );
    }

    @Test
    void shouldCancelLeaseSuccessfully() {

        LeaseWorkflowEngine engine = engine(
                mock(LeaseWorkflowValidator.class),
                mock(LeaseEventPublisher.class),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );

        Lease lease = createLease();

        engine.reject(lease, "tenant request");

        assertEquals(LeaseStatus.TERMINATED, lease.getStatus());
    }

    @Test
    void shouldPublishEventOnCancellation() {

        LeaseWorkflowValidator validator = mock(LeaseWorkflowValidator.class);
        LeaseEventPublisher publisher = mock(LeaseEventPublisher.class);
        LeaseRepository repo = mock(LeaseRepository.class);
        LeaseDomainService domainService = mock(LeaseDomainService.class);
        UnitOccupancyService occupancyService = mock(UnitOccupancyService.class);

        LeaseWorkflowEngine engine = engine(validator, publisher, repo, domainService, occupancyService);

        Lease lease = createLease();

        engine.reject(lease, "user cancel");

        verify(publisher, atLeastOnce()).publish(any());
    }
}