package com.rentmanager.modules.reservation.application.dto;

import java.util.UUID;

public record InitiateReservationResponse(
        UUID paymentIntentId
) {}