package com.rentmanager.modules.lease.workflow;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowValidator;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.LeaseDomainService;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for Addendum 4 §4.4's manual verification sequence
 * (steps 1, 2, 4). Step 3 (scheduler sweep) lives separately in
 * LeaseActionSchedulerExpiryTest, against LeaseActionScheduler's real
 * constructor. Step 5 (cron revert) is a commit-time checklist item per
 * §2.9, not something meaningfully assertable as a unit test.
 *
 * UPDATED: LeaseEventPublisher and the engine's direct publish() calls
 * were removed in the double-publish fix (see LeaseWorkflowEngine's
 * class-level doc). registerEvent()/pullDomainEvents() is now the single
 * source of truth for every transition, including expire() — the
 * asymmetry this test originally guarded (expire published directly and
 * deliberately did NOT registerEvent) no longer exists by design. Step 2
 * below is rewritten to assert the same regression risk (a future
 * double-publish on expire) via pullDomainEvents() instead of a collected
 * publisher callback, since there is no longer a publisher to collect from.
 *
 * Signatures below confirmed against real Lease.java source:
 * - expire() is no-arg, now registers its event via registerEvent() like
 *   every other transition (no longer a special case).
 * - renew(LocalDate newStart, LocalDate newEnd, UUID tenantId, String actor)
 *   -- corrected from an earlier guessed (LocalDate, BigDecimal) shape.
 * - isCancelled() lives on LeaseStatus, not Lease -- corrected from an
 *   earlier guessed lease.isCancelled() call to lease.getStatus().isCancelled().
 */
class LeaseExpireCancelRenewWorkflowTest {

    private Lease createLease(LeaseType leaseType) {
        return Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-" + System.nanoTime(),
                leaseType,
                BillingCycle.MONTHLY,
                LocalDate.now().minusMonths(13),
                LocalDate.now().minusDays(1),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );
    }

    // Real constructor confirmed against LeaseTenantIsolationServiceTest's
    // engine() factory. No publisher argument anymore -- see class doc.
    private LeaseWorkflowEngine createEngine() {
        return new LeaseWorkflowEngine(
                new LeaseWorkflowValidator(),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );
    }

    // =========================
    // §4.4 STEP 1: CANCEL on AWAITING_DEPOSIT
    // =========================
    // NOTE: this only covers the domain/engine-level assertion (status
    // becomes CANCELLED, not TERMINATED -- the §3.2 bug this session
    // fixed). The "GET on that lease doesn't 500" half of step 1 is a
    // LeaseApplicationService/DTO-mapping concern, already covered
    // separately by LeaseStatusDTOAlignmentTest -- not re-tested here to
    // avoid duplicating that test's job.
    @Test
    void cancel_awaitingDepositLease_setsStatusCancelled_notTerminated() {
        LeaseWorkflowEngine engine = createEngine();

        Lease lease = createLease(LeaseType.STANDARD);
        lease.approve();
        lease.markAwaitingDeposit();

        engine.cancel(lease, "deal fell through before move-in");

        assertEquals(LeaseStatus.CANCELLED, lease.getStatus());
        assertTrue(lease.getStatus().isCancelled());
        assertNotEquals(LeaseStatus.TERMINATED, lease.getStatus());
    }

    // =========================
    // §4.4 STEP 2: EXPIRE on ACTIVE — exactly one event
    // =========================
    // This is the one that guards against someone reintroducing a double-
    // publish on expire (e.g. adding a direct publisher call back in
    // alongside registerEvent()). If that regression happens, this test
    // starts asserting 1 == 2 and fails loudly.
    @Test
    void expire_activeLease_setsStatusExpired_andRegistersExactlyOneEvent() {
        LeaseWorkflowEngine engine = createEngine();

        Lease lease = createLease(LeaseType.STANDARD);
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        // Drain events from approve/markAwaitingDeposit/activate so the
        // count below isolates expire()'s contribution only.
        lease.pullDomainEvents();

        engine.expire(lease);

        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());

        List<DomainEvent> publishedEvents = lease.pullDomainEvents();
        assertEquals(
                1,
                publishedEvents.size(),
                "Expected exactly one event registered via Lease.expire()'s "
                        + "registerEvent() call. A count of 2 here means expire() is "
                        + "somehow registering twice, or a direct publisher call was "
                        + "reintroduced alongside it -- reintroducing the double-publish bug."
        );
    }

    // =========================
    // §4.4 STEP 4: RENEW an EXPIRED lease → RENEWED, then
    // TERMINATE/EXPIRE/RENEW all legal from RENEWED
    // =========================
    @Test
    void renew_expiredLease_reachesRenewed_thenTerminateExpireRenewAllLegalFromRenewed() {
        Lease lease = createLease(LeaseType.STANDARD);
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        lease.expire();

        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());

        lease.renew(LocalDate.now(), LocalDate.now().plusYears(1), lease.getTenantId(), "SYSTEM");

        assertEquals(LeaseStatus.RENEWED, lease.getStatus());

        // TERMINATE legal from RENEWED (§2.2/§2.3 guard expansion)
        Lease forTermination = createLease(LeaseType.STANDARD);
        forTermination.approve();
        forTermination.markAwaitingDeposit();
        forTermination.activate();
        forTermination.expire();
        forTermination.renew(LocalDate.now(), LocalDate.now().plusYears(1), forTermination.getTenantId(), "SYSTEM");
        assertDoesNotThrow(() -> forTermination.terminate(
                TerminationType.TENANT_REQUEST,
                "valid from RENEWED",
                "SYSTEM",
                forTermination.getTenantId()
        ));
        assertEquals(LeaseStatus.TERMINATED, forTermination.getStatus());

        // EXPIRE legal from RENEWED
        Lease forExpiry = createLease(LeaseType.STANDARD);
        forExpiry.approve();
        forExpiry.markAwaitingDeposit();
        forExpiry.activate();
        forExpiry.expire();
        forExpiry.renew(LocalDate.now(), LocalDate.now().plusYears(1), forExpiry.getTenantId(), "SYSTEM");
        assertDoesNotThrow(forExpiry::expire);
        assertEquals(LeaseStatus.EXPIRED, forExpiry.getStatus());

        // RENEW legal again from RENEWED (re-renewal)
        assertDoesNotThrow(() ->
                lease.renew(LocalDate.now(), LocalDate.now().plusYears(2), lease.getTenantId(), "SYSTEM")
        );
        assertEquals(LeaseStatus.RENEWED, lease.getStatus());
    }
}