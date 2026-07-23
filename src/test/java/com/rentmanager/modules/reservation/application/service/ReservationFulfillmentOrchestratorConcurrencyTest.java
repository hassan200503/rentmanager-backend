package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
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
 * asserts the loser's ObjectOptimisticLockingFailureException propagates
 * out of on() undisturbed — proving step 0 rethrows rather than swallows.
 *
 * ---- Updated for the step-0-extraction + decision-A fix ----
 *
 * Step 0 (markFulfilling) now lives in ReservationFulfillmentStepZeroService,
 * its own bean, committed in its own REQUIRES_NEW transaction, called
 * from on() BEFORE the rest of the saga. Decision A (confirmed explicitly)
 * kept this service's version-conflict handling identical to the
 * pre-refactor inline code: it does NOT catch
 * ObjectOptimisticLockingFailureException, so it propagates out of
 * markFulfilling(), and on() does not catch it either — it propagates out
 * of on() exactly as before. The barrier/read-count mechanics below are
 * UNCHANGED and still correct: the spied ReservationRepository is the same
 * shared bean regardless of which service (stepZeroService or on() itself)
 * calls findById() on it, so the first two invocations across both threads
 * are still exactly the two threads' step-0 reads, and the barrier still
 * forces them to hold the identical stale version before either proceeds.
 *
 * CHANGED IN THIS REVISION: previously this test left unitRepository (and
 * everything downstream of step 0) unstubbed, relying on the winning
 * thread's on() call swallowing any resulting failure and returning
 * normally. That assumption broke once the orchestrator's generic catch
 * was changed to rethrow after compensating (decision 1, a separate,
 * earlier change) — an unstubbed unitRepository now causes the winning
 * thread to compensate AND throw, which is not what this test is about.
 * The winning path is now fully stubbed to complete the ENTIRE happy path
 * for real, so "completed normally" actually means what it says, and this
 * test is cleanly scoped to just the step-0 race, per its own original
 * intent.
 *
 * SCOPE NOTE (unchanged): this test does not need to assert anything
 * about compensation logic itself — with the winning path now completing
 * for real, compensate() should never be invoked at all in this test.
 *
 * ASSUMPTION (unverified against source): ClerkUserCreationResult is a
 * record (String clerkUserId, boolean newlyCreated); Unit.rehydrate(...)
 * has the same parameter shape used elsewhere in this test suite
 * (ReservationFulfillmentOrchestratorStepSixAndGenericFailureTest). If
 * either differs from the real source, adjust construction accordingly.
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
    // consumer (including ReservationFulfillmentStepZeroService and the
    // orchestrator itself) after context refresh, so stubbing findById()
    // here affects both.
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
    @MockBean private RentLedgerApplicationService rentLedgerApplicationService;

    @Test
    void concurrentFulfillment_loserIsRethrownNotCompensated_winnerCompletesNormally() throws Exception {

        UUID unitId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        BigDecimal depositAmount = new BigDecimal("50000");
        UUID landlordTenantId = UUID.randomUUID();
        PaymentIntent paymentIntent = PaymentIntent.create(
                landlordTenantId,
                unitId,
                propertyId,
                "{\"fullName\":\"Jane Tenant\"}",
                depositAmount
        );

        paymentIntent.attachCheckoutRequestId("ws_CO_TEST_" + UUID.randomUUID());
        paymentIntent.markPaid("SANDBOX_RECEIPT_1");
        paymentIntent = paymentIntentRepository.save(paymentIntent);

        Reservation reservation = Reservation.create(
                unitId,
                propertyId,
                "Jane Tenant",
                "+254700000000",
                "jane@example.com",
                "12345678",
                "+254700000000",
                LocalDate.now().plusDays(7),
                depositAmount,
                paymentIntent.getId()
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

        doNothing().when(fulfillmentValidator).validate(any(), anyBoolean(), anyBoolean());
        when(leaseRepository.findByUnitIdAndStatus(any(), any())).thenReturn(Optional.empty());

        String clerkUserId = "clerk_test_user_id";
        when(clerkService.createTenantUser(any(), any(), any(), any()))
                .thenReturn(new ClerkUserCreationResult(clerkUserId, true));

        // Full happy-path stubs so the WINNING thread genuinely completes
        // steps 1-6, rather than tripping over an unstubbed mock and
        // getting compensated/rethrown for an unrelated reason.
        Unit unit = Unit.rehydrate(
                unitId,
                landlordTenantId,
                propertyId,
                "A-101",
                "Unit A-101",
                null,
                UnitStatus.ACTIVE,
                UnitOccupancyStatus.VACANT,
                BigDecimal.valueOf(15000),
                null,
                "Nice unit",
                null
        );
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));
        when(unitRepository.save(any(Unit.class))).thenAnswer(inv -> inv.getArgument(0));

        when(tenantProfileRepository.findByTenantIdAndClerkUserId(landlordTenantId, clerkUserId))
                .thenReturn(Optional.empty());
        when(tenantProfileRepository.save(any(TenantProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(leaseRepository.save(any(Lease.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        doNothing().when(eventPublisher).publishAll(anyList());

        // Force both threads to complete their findById() read of the SAME
        // reservation row — holding the identical stale version — before
        // either proceeds to markFulfilling()+flush() inside
        // ReservationFulfillmentStepZeroService. Scoped to exactly the
        // first 2 invocations: those are necessarily each thread's step-0
        // read (see class javadoc for why this still holds after the
        // step-0 extraction). The winning thread's LATER re-fetch inside
        // on() is a 3rd call and correctly falls outside this gate.
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

        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = List.of(
                pool.submit(() -> orchestrator.on(event)),
                pool.submit(() -> orchestrator.on(event))
        );

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

        assertThat(failures)
                .as("exactly one thread should lose the version race at step 0")
                .hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(completedNormally)
                .as("the winning thread should complete the full saga normally")
                .isEqualTo(1);

        // With the winning path now genuinely successful, compensate()
        // should never be invoked at all in this test.
        verify(compensationService, never()).compensate(any(), any(), any());
    }
}