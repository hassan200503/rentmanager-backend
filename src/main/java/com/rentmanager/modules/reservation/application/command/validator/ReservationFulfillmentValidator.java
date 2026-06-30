package com.rentmanager.modules.reservation.application.command.validator;

import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.enums.ReservationStatus;
import org.springframework.stereotype.Component;


@Component
public class ReservationFulfillmentValidator {

    public void validate(Reservation reservation,
                         boolean paymentExists,
                         boolean leaseExists) {

        if (reservation == null) {
            throw new IllegalStateException("Reservation does not exist");
        }

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled reservation cannot be fulfilled");
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new IllegalStateException("Reservation is already completed");
        }

        if (!paymentExists) {
            throw new IllegalStateException("Payment record not found");
        }

        if (leaseExists) {
            throw new IllegalStateException("Lease already exists for this reservation");
        }
    }
}