package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Answers Open Question #1 from the handoff doc, and stands as the
 * regression test for its fix: fires ReservationDepositPaidEvent for the
 * SAME reservation from two threads simultaneously, forced (via a barrier
 * on the reservation read) to hold the identical stale @Version, and
 * asserts the loser's ObjectOptimisticLockingFailureException is caught
 * inside the orchestrator's own try/catch at the markFulfilling() flush —
 * not left to surface invisibly at commit, after on() has already returned.
 *
 * ---- Updated for the Option A + B1 fix ----
 *
 * The orchestrator's step-0 (markFulfilling) version-conflict handling was
 * changed from "catch, log, call compensate(), return normally" to "catch,
 * log, RETHROW — no compensate() call." This was a deliberate policy
 * decision (Option A): at step 0 nothing has been created yet, so there is
 * nothing to compensate, and simply returning normally after a failed
 * flush() was found to commit a poisoned Hibernate session, corrupting the
 * JDBC connection (see PSQLException/EOFException in the session's own
 * prior test run). Rethrowing is safe because on() only runs as an
 * AFTER_COMMIT transactional event listener callback in production — Spring
 * swallows and logs Throwables from those callbacks without affecting the
 * already-committed outer transaction. In THIS test, on() is invoked
 * directly (not via the real event-publishing mechanism), so the rethrown
 * exception surfaces as an ExecutionException wrapping
 * ObjectOptimisticLockingFailureException on the losing thread's Future —
 * that is expected and asserted on below, not a test failure.
 *
 * Only ReservationRepository is real (Testcontainers Postgres). Everything
 * downstream of step 0 (Clerk, SMS, TenantProfile, Lease, Unit) is mocked,
 * since the losing thread never reaches step 1 — it dies at the flush()
 * immediately after markFulfilling(), which is exactly the behavior under
 * test.
 *
 * SCOPE NOTE: this test does NOT assert on the winning thread's final
 * outcome or on the reservation's terminal status. The winning thread still
 * depends on unitRepository (an unstubbed @MockBean) and on
 * ReservationFulfillmentCompensationService's re-fetch, which has a
 * SEPARATE, already-identified, not-yet-fixed transaction-isolation bug
 * (compensate()'s REQUIRES_NEW re-fetch can't see the winner's own
 * still-uncommitted markFulfilling() write under READ COMMITTED, so
 * markFulfillmentFailed() can throw IllegalStateException and leave the
 * reservation stuck in FULFILLING). Asserting a terminal status here would
 * conflate that unrelated bug with this fix and produce a misleading
 * failure. That issue needs its own fix and its own test — flagged, not
 * silently addressed here.
 *
 * ASSUMPTION (unverified against source): ClerkService.createTenantUser(...)
 * returns a ClerkUserCreationResult shaped as a record with
 * (String clerkUserId, boolean newlyCreated). If the winning thread's
 * stubbing fails to compile, adjust this construction to match the real
 * record/class shape.
 */
@SpringBootTest
@Testcontainers
class ReservationFulfillmentOrchestratorConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("rentmanager_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }

    @Autowired
    private ReservationFulfillmentOrchestrator orchestrator;

    // Real bean, wrapped as a spy — Spring substitutes this spy into every
    // consumer (including the orchestrator) after context refresh, so
    // stubbing findById() here actually affects production code under test.
    @SpyBean
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentIntentRepository paymentIntentRepository;

    @SpyBean
    private ReservationFulfillmentCompensationService compensationService;

    @MockBean private ClerkService clerkService;
    @MockBean private SmsService smsService;
    @MockBean private TenantProfileRepository tenantProfileRepository;
    @MockBean private LeaseRepository leaseRepository;
    @MockBean private UnitRepository unitRepository;
    @MockBean private DomainEventPublisher eventPublisher;
    @MockBean private ReservationFulfillmentValidator fulfillmentValidator;

    @Test
    void concurrentFulfillment_loserIsRethrownNotCompensated() throws Exception {

        // ---------------------------------------------------------------
        // Arrange: seed a real PAID PaymentIntent first — reservations.
        // payment_intent_id carries a real FK constraint on Postgres, so
        // a random UUID here fails the insert with a constraint violation.
        // ---------------------------------------------------------------
        UUID unitId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        BigDecimal depositAmount = new BigDecimal("50000");

        PaymentIntent paymentIntent = PaymentIntent.create(
                unitId,
                propertyId,
                "{\"fullName\":\"Jane Tenant\"}", // formDataJson placeholder
                depositAmount
        );
        paymentIntent.attachCheckoutRequestId("ws_CO_TEST_" + UUID.randomUUID());
        paymentIntent.markPaid("SANDBOX_RECEIPT_1");
        paymentIntent = paymentIntentRepository.save(paymentIntent);

        // ---------------------------------------------------------------
        // Arrange: a reservation in DEPOSIT_PAID, ready for fulfillment.
        // ---------------------------------------------------------------
        Reservation reservation = Reservation.create(
                unitId,
                propertyId,
                "Jane Tenant",
                "+254700000000",
                "jane@example.com",
                "12345678",
                "+254700000000",            // mpesaPhone
                LocalDate.now().plusDays(7),
                depositAmount,
                paymentIntent.getId()       // real, persisted paymentIntentId
        );
        reservation.markDepositPaid("SANDBOX_RECEIPT_1");
        reservation = reservationRepository.save(reservation);
        UUID reservationId = reservation.getId();

        ReservationDepositPaidEvent event = new ReservationDepositPaidEvent(
                reservationId,
                reservation.getUnitId(),
                reservation.getPropertyId(),
                reservation.getFullName(),
                reservation.getPhone(),
                reservation.getEmail(),
                reservation.getNationalId(),
                reservation.getMpesaPhone(),
                "SANDBOX_RECEIPT_1",
                reservation.getDepositAmount(),
                reservation.getMoveInDate()
        );

        // No-op validation — we're testing the version race, not policy rules.
        doNothing().when(fulfillmentValidator).validate(any(), anyBoolean(), anyBoolean());
        when(leaseRepository.findByUnitIdAndStatus(any(), any())).thenReturn(Optional.empty());

        // ASSUMPTION: ClerkUserCreationResult is a record (String clerkUserId,
        // boolean newlyCreated) — inferred from its usage in the orchestrator
        // (clerkResult.clerkUserId(), clerkResult.newlyCreated()), not
        // verified against the actual file. If this line fails to compile,
        // paste ClerkUserCreationResult.java and this will need adjusting.
        // Only relevant to whichever thread wins the step-0 race and
        // proceeds past it — the loser never reaches this stub.
        when(clerkService.createTenantUser(any(), any(), any(), any()))
                .thenReturn(new ClerkUserCreationResult("clerk_test_user_id", true));

        // Force both threads to complete their findById() read of the SAME
        // reservation row — and therefore hold the identical stale version —
        // before either is allowed to proceed to markFulfilling()+flush().
        // Without this, the two threads might not actually overlap and the
        // test would pass or fail based on luck rather than proving anything.
        //
        // Scoped to exactly the first 2 invocations: on() calls findById()
        // once per thread at the top of the method (2 total). Any LATER
        // call with this same id — compensate()'s re-fetch, or this test's
        // own final lookup — must pass straight through. The barrier is
        // built for exactly 2 parties; letting a 3rd or 4th caller touch it
        // times out one side and throws BrokenBarrierException on the
        // other, poisoning both REQUIRES_NEW transactions with an unrelated
        // failure.
        CyclicBarrier bothThreadsHaveRead = new CyclicBarrier(2);
        UUID targetId = reservationId;
        AtomicInteger readCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (readCount.incrementAndGet() <= 2) {
                bothThreadsHaveRead.await(5, TimeUnit.SECONDS);
            }
            return result;
        }).when(reservationRepository).findById(targetId);

        // ---------------------------------------------------------------
        // Act: fire the same event from two threads concurrently.
        // ---------------------------------------------------------------
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = List.of(
                pool.submit(() -> orchestrator.on(event)),
                pool.submit(() -> orchestrator.on(event))
        );

        // Collect each future's outcome individually rather than assuming
        // which thread wins the barrier race — that's non-deterministic.
        List<Throwable> failures = new ArrayList<>();
        int completedNormally = 0;
        for (Future<?> f : futures) {
            try {
                f.get(15, TimeUnit.SECONDS);
                completedNormally++;
            } catch (ExecutionException ex) {
                failures.add(ex.getCause());
            }
        }
        pool.shutdown();

        // ---------------------------------------------------------------
        // Assert
        // ---------------------------------------------------------------

        // Exactly one thread must lose the version race at the step-0
        // flush, and that loss must surface as a propagated
        // ObjectOptimisticLockingFailureException — proving on() rethrows
        // rather than swallowing it. (In production this is caught and
        // logged by Spring's AFTER_COMMIT listener machinery instead of
        // reaching test code, but the rethrow itself is what's under test
        // here.)
        assertThat(failures)
                .as("exactly one thread should lose the version race at markFulfilling()")
                .hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // The other thread's on() call must return normally — i.e. NOT
        // throw ObjectOptimisticLockingFailureException itself. (It may
        // still internally hit compensate() for an unrelated reason, e.g.
        // the unstubbed unitRepository — that's out of scope here, see
        // class javadoc.)
        assertThat(completedNormally)
                .as("the winning thread's on() call should return normally")
                .isEqualTo(1);

        // The real assertion: the step-0 version conflict must NEVER be
        // handed to compensate(). If the old catch-log-compensate-return
        // behavior were reintroduced, this fails — proving Option A
        // (silent abort, no compensation) is actually in effect.
        verify(compensationService, never()).compensate(
                any(),
                eq(reservationId),
                any(ObjectOptimisticLockingFailureException.class)
        );
    }
}