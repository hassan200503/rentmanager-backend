package com.rentmanager.modules.tenant.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
@Builder
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SubscriptionPeriod {

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // --------------------------------------------------
    // FACTORY (SAFE CONSTRUCTION)
    // --------------------------------------------------

    public static SubscriptionPeriod of(LocalDate startDate, LocalDate endDate) {

        validate(startDate, endDate);

        return SubscriptionPeriod.builder()
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    // --------------------------------------------------
    // DOMAIN BEHAVIOR
    // --------------------------------------------------

    public boolean isActive(LocalDate date) {

        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        return (startDate == null || !date.isBefore(startDate))
                && (endDate == null || !date.isAfter(endDate));
    }

    public boolean isExpired(LocalDate date) {

        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        return endDate != null && date.isAfter(endDate);
    }

    public long daysRemaining(LocalDate date) {

        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }

        if (endDate == null || date.isAfter(endDate)) {
            return 0;
        }

        return ChronoUnit.DAYS.between(date, endDate);
    }

    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    private static void validate(LocalDate startDate, LocalDate endDate) {

        if (startDate == null) {
            throw new IllegalArgumentException("Start date is required");
        }

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "End date cannot be before start date"
            );
        }
    }
}