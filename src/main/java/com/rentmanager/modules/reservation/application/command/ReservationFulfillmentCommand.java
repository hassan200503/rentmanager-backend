package com.rentmanager.modules.reservation.application.command;

import java.util.UUID;

/**
 * Carries only the data required to fulfill a paid reservation.
 */
public record ReservationFulfillmentCommand(
        UUID reservationId,
        UUID unitId,
        UUID tenantId,
        UUID paymentId
) {}