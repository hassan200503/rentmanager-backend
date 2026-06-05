package com.rentmanager.modules.unit.domain.valueobject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record RentalPeriod(
        LocalDate startDate,
        LocalDate endDate
) {

    public RentalPeriod {

        Objects.requireNonNull(startDate, "Start date is required");

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
    }

    // --------------------------------------------------
    // DOMAIN BEHAVIOR
    // --------------------------------------------------

    public long durationInDays() {

        if (endDate == null) {
            return 0; // open-ended lease
        }

        return ChronoUnit.DAYS.between(startDate, endDate);
    }

    public boolean isActive(LocalDate date) {

        Objects.requireNonNull(date, "Date is required");

        boolean startsOk = !date.isBefore(startDate);
        boolean notEnded = (endDate == null) || !date.isAfter(endDate);

        return startsOk && notEnded;
    }

    public boolean isFuture(LocalDate date) {

        Objects.requireNonNull(date, "Date is required");

        return date.isBefore(startDate);
    }

    public boolean isExpired(LocalDate date) {

        Objects.requireNonNull(date, "Date is required");

        return endDate != null && date.isAfter(endDate);
    }
}