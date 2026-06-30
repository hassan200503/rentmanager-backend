package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.application.command.usecase.ReservationFulfillmentUseCase;

import java.util.UUID;

public class ReservationCommandService {

    private final ReservationFulfillmentUseCase fulfillmentUseCase;

    public ReservationCommandService(ReservationFulfillmentUseCase fulfillmentUseCase) {
        this.fulfillmentUseCase = fulfillmentUseCase;
    }

    public UUID fulfillReservation(UUID reservationId) {
        return fulfillmentUseCase.handle(reservationId);
    }
}