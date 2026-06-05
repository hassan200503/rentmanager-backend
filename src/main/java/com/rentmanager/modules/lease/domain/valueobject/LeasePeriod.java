package com.rentmanager.modules.lease.domain.valueobject;

import java.time.LocalDate;

public class LeasePeriod {

    private final LocalDate startDate;
    private final LocalDate endDate;

    public LeasePeriod(LocalDate startDate, LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Lease dates cannot be null");
        }

        if (!endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("End date must be after start date");
        }

        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActiveOn(LocalDate date) {
        return (date.isEqual(startDate) || date.isAfter(startDate))
                && date.isBefore(endDate);
    }

    public long getDurationInDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
    }
}