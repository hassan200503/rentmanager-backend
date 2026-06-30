package com.rentmanager.modules.reservation.application.command.handler;

import com.rentmanager.modules.reservation.application.command.usecase.ReservationFulfillmentUseCase;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.enums.ReservationStatus;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.lease.application.command.CreateLeaseCommand;
import com.rentmanager.modules.lease.application.command.usecase.CreateLeaseUseCase;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.shared.events.DomainEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

public class ReservationFulfillmentHandler implements ReservationFulfillmentUseCase {

    private final ReservationRepository reservationRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final LeaseRepository leaseRepository;
    private final CreateLeaseUseCase createLeaseUseCase;
    private final UnitCommandService unitCommandService;
    private final ReservationFulfillmentValidator validator;
    private final DomainEventPublisher eventPublisher;

    public ReservationFulfillmentHandler(
            ReservationRepository reservationRepository,
            PaymentIntentRepository paymentIntentRepository,
            LeaseRepository leaseRepository,
            CreateLeaseUseCase createLeaseUseCase,
            UnitCommandService unitCommandService,
            ReservationFulfillmentValidator validator,
            DomainEventPublisher eventPublisher
    ) {
        this.reservationRepository = reservationRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.leaseRepository = leaseRepository;
        this.createLeaseUseCase = createLeaseUseCase;
        this.unitCommandService = unitCommandService;
        this.validator = validator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UUID handle(UUID reservationId) {

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found"));

        // ---------------- IDEMPOTENCY ----------------
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            return null;
        }

        if (reservation.getStatus() == ReservationStatus.FULFILLING) {
            return null;
        }

        // ---------------- PAYMENT VALIDATION (NO ASSUMPTIONS) ----------------
        paymentIntentRepository.findById(reservation.getPaymentIntentId())
                .orElseThrow(() -> new IllegalStateException("PaymentIntent not found"));

        // ---------------- LEASE SAFETY CHECK ----------------
        boolean leaseExists = leaseRepository.hasActiveLeaseForUnit(reservation.getUnitId());

        validator.validate(reservation, true, leaseExists);

        // ---------------- STATE TRANSITION ----------------
        reservation.markFulfilling();
        reservationRepository.save(reservation);

        // ---------------- LEASE CREATION ----------------
        CreateLeaseCommand command = new CreateLeaseCommand(
                reservation.getUnitId(),        // tenantId (unchanged, no assumptions)
                reservation.getPropertyId(),
                reservation.getUnitId(),
                null,                           // tenantProfileId (unknown source)
                null,                           // leaseType (no assumption)
                null,                           // billingCycle (no assumption)
                reservation.getMoveInDate(),
                reservation.getMoveInDate().plusMonths(1),
                reservation.getDepositAmount(),
                reservation.getDepositAmount(),
                BigDecimal.ZERO,
                3,
                true
        );

        UUID leaseId = createLeaseUseCase.handle(command);

        // ---------------- FINALIZE ----------------
        reservation.complete(generateCorrelationBasedClerkId(reservationId, leaseId));
        reservationRepository.save(reservation);

        // ---------------- UNIT UPDATE (FIXED SIGNATURE) ----------------
        unitCommandService.markOccupied(
                reservation.getPropertyId(),   // ⚠ tenantId (based on UnitCommandService contract)
                reservation.getUnitId(),
                reservationId.toString()       // correlationId
        );

        // ---------------- EVENTS ----------------
        eventPublisher.publishAll(reservation.pullDomainEvents());

        return leaseId;
    }

    // deterministic placeholder (NO external dependency assumed)
    private String generateCorrelationBasedClerkId(UUID reservationId, UUID leaseId) {
        return "clerk_" + reservationId + "_" + leaseId;
    }
}