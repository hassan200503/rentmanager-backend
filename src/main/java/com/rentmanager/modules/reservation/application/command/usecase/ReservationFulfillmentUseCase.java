package com.rentmanager.modules.reservation.application.command.usecase;

import java.util.UUID;

public interface ReservationFulfillmentUseCase {

    UUID handle(UUID reservationId);
}