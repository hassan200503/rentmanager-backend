package com.rentmanager.modules.lease.scheduler;

import com.rentmanager.modules.lease.application.orchestration.LeaseActivationOrchestrator;
import com.rentmanager.modules.lease.application.scheduler.LeaseActionScheduler;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.domain.service.LeaseDomainService;
import com.rentmanager.modules.lease.domain.service.UnitOccupancyService;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowEngine;
import com.rentmanager.modules.lease.domain.workflow.LeaseWorkflowValidator;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * §4.4 step 3 coverage: FIXED_TERM/STANDARD sweep-eligibility and the
 * MONTH_TO_MONTH exclusion (§1.4).
 *
 * UPDATE (this session, Decision 3): expireOne() now carries its own
 * independent MONTH_TO_MONTH guard, separate from the leaseType filter
 * in runDailyExpiry()'s repository query. The gap flagged below when
 * this file was originally written -- that expireOne() had no
 * independent lease-type check -- has been closed. The guard throws
 * LeaseStateException with ErrorCode.LEASE_EXPIRATION_NOT_APPLICABLE_TO_MONTH_TO_MONTH
 * when expireOne() is called directly on a MONTH_TO_MONTH lease,
 * mirroring how activateOne()'s guard is independent of runDaily()'s
 * query. Covered directly below by
 * expireOne_monthToMonthLease_throwsLeaseStateException().
 *
 * UPDATED (this session): LeaseEventPublisher removed from
 * LeaseWorkflowEngine's constructor as part of the double-publish fix.
 * No test in this file asserted against the publisher directly, so this
 * is a pure constructor-call fix -- no behavioral change.
 */
class LeaseActionSchedulerExpiryTest {

    private Lease createLease(LeaseType leaseType, LeaseStatus targetStatus) {
        Lease lease = Lease.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LS-" + System.nanoTime(),
                leaseType,
                BillingCycle.MONTHLY,
                LocalDate.now().minusMonths(13),
                LocalDate.now().minusDays(1), // past end date
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                new BigDecimal("1000"),
                7,
                false
        );
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();

        if (targetStatus == LeaseStatus.RENEWED) {
            lease.expire();
            lease.renew(LocalDate.now(), LocalDate.now().plusYears(1), lease.getTenantId(), "SYSTEM");
        }


        return lease;
    }

    private LeaseActionScheduler schedulerWithMockedRepository(LeaseRepository repository) {
        LeaseWorkflowEngine realEngine = new LeaseWorkflowEngine(
                new LeaseWorkflowValidator(),
                mock(LeaseRepository.class),
                mock(LeaseDomainService.class),
                mock(UnitOccupancyService.class)
        );
        return new LeaseActionScheduler(
                repository,
                mock(DomainEventPublisher.class),
                mock(LeaseActivationOrchestrator.class),
                realEngine
        );
    }

    // =========================
    // §4.4 STEP 3a: sweep query excludes MONTH_TO_MONTH
    // =========================
    @Test
    void runDailyExpiry_queriesRepositoryWithFixedTermAndStandardOnly_excludingMonthToMonth() {
        LeaseRepository repository = mock(LeaseRepository.class);
        when(repository.findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(
                anyList(), anyList(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        LeaseActionScheduler scheduler = schedulerWithMockedRepository(repository);

        scheduler.runDailyExpiry();

        ArgumentCaptor<List<LeaseType>> leaseTypesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(
                anyList(), leaseTypesCaptor.capture(), any(LocalDate.class));

        List<LeaseType> queriedTypes = leaseTypesCaptor.getValue();
        assertTrue(queriedTypes.contains(LeaseType.FIXED_TERM));
        assertTrue(queriedTypes.contains(LeaseType.STANDARD));
        assertFalse(
                queriedTypes.contains(LeaseType.MONTH_TO_MONTH),
                "MONTH_TO_MONTH must never be included in the expiry sweep query -- "
                        + "its endDate is nominal, not a real contractual expiry (§1.4)."
        );
    }

    // =========================
    // §4.4 STEP 3b: FIXED_TERM with past endDate actually expires
    // =========================
    @Test
    void expireOne_fixedTermActiveLeaseWithPastEndDate_expiresAndSaves() {
        LeaseRepository repository = mock(LeaseRepository.class);
        LeaseActionScheduler scheduler = schedulerWithMockedRepository(repository);

        Lease lease = createLease(LeaseType.FIXED_TERM, LeaseStatus.ACTIVE);

        scheduler.expireOne(lease);

        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());
        verify(repository).save(lease);
    }

    // =========================
    // §4.4 STEP 3c: RENEWED lease also eligible (per §1.3 equivalence)
    // =========================
    @Test
    void expireOne_renewedLeaseWithPastEndDate_expiresAndSaves() {
        LeaseRepository repository = mock(LeaseRepository.class);
        LeaseActionScheduler scheduler = schedulerWithMockedRepository(repository);

        Lease lease = createLease(LeaseType.FIXED_TERM, LeaseStatus.RENEWED);

        scheduler.expireOne(lease);

        assertEquals(LeaseStatus.EXPIRED, lease.getStatus());
        verify(repository).save(lease);
    }

    // =========================
    // §4.4 STEP 3d (new, this session): expireOne() independently rejects
    // MONTH_TO_MONTH, per Decision 3's dedicated guard.
    // =========================
    @Test
    void expireOne_monthToMonthLease_throwsLeaseStateException() {
        LeaseRepository repository = mock(LeaseRepository.class);
        LeaseActionScheduler scheduler = schedulerWithMockedRepository(repository);

        Lease lease = createLease(LeaseType.MONTH_TO_MONTH, LeaseStatus.ACTIVE);

        LeaseStateException ex = assertThrows(LeaseStateException.class,
                () -> scheduler.expireOne(lease));

        assertEquals(ErrorCode.LEASE_EXPIRATION_NOT_APPLICABLE_TO_MONTH_TO_MONTH, ex.getErrorCode());
        verify(repository, never()).save(lease);
    }
}