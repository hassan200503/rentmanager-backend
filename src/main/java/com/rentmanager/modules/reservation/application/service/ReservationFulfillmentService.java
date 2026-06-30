package com.rentmanager.modules.reservation.application.service;

import java.util.UUID;

public interface ReservationFulfillmentService {

    void fulfill(UUID reservationId);

}