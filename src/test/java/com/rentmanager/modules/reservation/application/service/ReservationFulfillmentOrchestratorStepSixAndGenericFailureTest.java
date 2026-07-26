package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Covers the step-6 optimistic-lock collision and the generic (non-locking)
 * failure path in ReservationFulfillmentOrchestrator.on(...).
 *
 * ---- Fixed after first real test run (see mvn output) ----
 *
 * buildDepositPaidReservation() now ALSO calls markFulfilling() on the
 * fixture, in addition to markDepositPaid(). This is required because
 * ReservationFulfillmentStepZeroService is mocked in this test class
 * (@Mock, stubbed to return true) — a mock has no side effects on the
 * real Reservation object, unlike the real service, which durably
 * transitions status to FULFILLING before on() ever reaches step 1.
 * Without this, the fixture stayed at DEPOSIT_PAID, so step 6's
 * reservation.complete(clerkUserId) threw its OWN guard exception
 * ("Only FULFILLING reservations can be completed") before ever reaching
 * the mocked flush()/lockEx path — which was caught by the GENERIC catch
 * block instead of the optimistic-lock catch block, producing a wrapped
 * ReservationFulfillmentFailedException instead of the expected lockEx.
 * Confirmed via a real mvn test run reproducing exactly this failure.
 *
 * NOT YET RUN a second time after this fix — re-run and confirm green
 * before considering this file done.
 */
@ExtendWith(MockitoExtension.class)
class ReservationFulfillmentOrchestratorStepSixAndGenericFailureTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private TenantProfileRepository tenantProfileRepository;
    @Mock private LeaseRepository leaseRepository;
    @Mock private UnitRepository unitRepository;
    @Mock private ClerkService clerkService;
    @Mock private SmsService smsService;
    @Mock private ReservationFulfillmentValidator fulfillmentValidator;
    @Mock private DomainEventPublisher eventPublisher;
    @Mock private ReservationFulfillmentCompensationService compensationService;
    @Mock private ReservationFulfillmentStepZeroService stepZeroService;
    @Mock private RentLedgerApplicationService rentLedgerApplicationService;

    private ReservationFulfillmentOrchestrator orchestrator;

    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID paymentIntentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orchestrator = new ReservationFulfillmentOrchestrator(
                reservationRepository,
                tenantProfileRepository,
                leaseRepository,
                unitRepository,
                clerkService,
                smsService,
                fulfillmentValidator,
                eventPublisher,
                compensationService,
                stepZeroService,
                rentLedgerApplicationService
        );

        lenient().when(leaseRepository.findByUnitIdAndStatus(eq(unitId), eq(LeaseStatus.PENDING_ACTIVATION)))
                .thenReturn(Optional.empty());
        lenient().doNothing().when(fulfillmentValidator).validate(any(), anyBoolean(), anyBoolean());
        lenient().doNothing().when(eventPublisher).publishAll(anyList());
        lenient().when(stepZeroService.markFulfilling(any())).thenReturn(true);
    }

    // ---- Fixtures ----

    private Reservation buildDepositPaidReservation() {
        Reservation reservation = Reservation.create(
                unitId,
                propertyId,
                "Jane Wanjiru",
                "+254712345678",
                "jane@example.com",
                "12345678",
                "+254712345678",
                LocalDate.now().plusDays(5),
                BigDecimal.valueOf(15000),
                paymentIntentId
        );
        reservation.markDepositPaid("QGH7XYZ123");
        // Simulates what the (mocked) ReservationFulfillmentStepZeroService
        // would really have done in production — see class javadoc.
        reservation.markFulfilling();
        return reservation;
    }

    private ReservationDepositPaidEvent buildEvent(Reservation reservation) {
        return new ReservationDepositPaidEvent(
                reservation.getId(),
                reservation.getUnitId(),
                reservation.getPropertyId(),
                reservation.getFullName(),
                reservation.getPhone(),
                reservation.getEmail(),
                reservation.getNationalId(),
                reservation.getMpesaPhone(),
                reservation.getMpesaReceiptNumber(),
                reservation.getDepositAmount(),
                reservation.getMoveInDate()
        );
    }

    private Unit buildVacantUnit() {
        return Unit.rehydrate(
                unitId,
                UUID.randomUUID(), // tenantId (landlord account)
                propertyId,
                "A-101",
                "Unit A-101",
                null,
                UnitStatus.ACTIVE,
                UnitOccupancyStatus.VACANT,
                BigDecimal.valueOf(15000),
                null,
                "Nice unit",
                null,
                null
        );
    }

    // ---- Step 6 collision: real work done this run, MUST compensate ----

    @Test
    void stepSixCollision_compensatesWithAccurateSagaState_thenRethrows() {
        Reservation reservation = buildDepositPaidReservation();
        ReservationDepositPaidEvent event = buildEvent(reservation);
        Unit unit = buildVacantUnit();
        String clerkUserId = "clerk_user_123";

        when(reservationRepository.findById(reservation.getId()))
                .thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ObjectOptimisticLockingFailureException lockEx =
                new ObjectOptimisticLockingFailureException(Reservation.class, reservation.getId());
        doThrow(lockEx).when(reservationRepository).flush();

        when(clerkService.createTenantUser(
                eq(reservation.getFullName()),
                eq(reservation.getEmail()),
                eq(reservation.getPhone()),
                any(String.class))
        ).thenReturn(new ClerkUserCreationResult(clerkUserId, true));

        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        when(tenantProfileRepository.findByTenantIdAndClerkUserId(unit.getTenantId(), clerkUserId))
                .thenReturn(Optional.empty());
        when(tenantProfileRepository.save(any(TenantProfile.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(leaseRepository.save(any(Lease.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        when(unitRepository.save(any(Unit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> orchestrator.on(event))
                .isSameAs(lockEx);

        verify(smsService).sendCredentials(eq(reservation.getPhone()), any(String.class));
        verify(smsService, never()).sendReservationConfirmed(any());

        ArgumentCaptor<SagaState> sagaCaptor = ArgumentCaptor.forClass(SagaState.class);
        verify(compensationService, times(1))
                .compensate(sagaCaptor.capture(), eq(reservation.getId()), eq(lockEx));

        SagaState captured = sagaCaptor.getValue();
        assertThat(captured.clerkUserId).isEqualTo(clerkUserId);
        assertThat(captured.clerkUserCreatedThisRun).isTrue();
        assertThat(captured.tenantProfileCreatedThisRun).isTrue();
        assertThat(captured.tenantProfileId).isNotNull();
        assertThat(captured.leaseCreated).isTrue();
        assertThat(captured.leaseId).isNotNull();
        assertThat(captured.unitReserved).isTrue();
        assertThat(captured.unitId).isEqualTo(unitId);
    }

    // ---- Generic mid-saga failure: compensates AND rethrows (decision 1) ----

    @Test
    void genericFailureMidSaga_compensatesThenRethrows() {
        Reservation reservation = buildDepositPaidReservation();
        ReservationDepositPaidEvent event = buildEvent(reservation);

        when(reservationRepository.findById(reservation.getId()))
                .thenReturn(Optional.of(reservation));

        RuntimeException clerkFailure = new RuntimeException("Clerk API unavailable");
        when(clerkService.createTenantUser(any(), any(), any(), any()))
                .thenThrow(clerkFailure);

        assertThatThrownBy(() -> orchestrator.on(event))
                .isInstanceOf(ReservationFulfillmentFailedException.class)
                .hasCause(clerkFailure);

        verify(compensationService, times(1))
                .compensate(any(SagaState.class), eq(reservation.getId()), eq(clerkFailure));

        verifyNoInteractions(smsService);
        verifyNoInteractions(tenantProfileRepository);
        verify(leaseRepository, never()).save(any());
        verify(unitRepository, never()).save(any());
    }
}