package com.rentmanager.modules.unit.domain.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

public record UnitPricing(
        BigDecimal baseRent,
        BigDecimal securityDeposit,
        BigDecimal maintenanceFee
) {

    public UnitPricing {

        Objects.requireNonNull(baseRent, "Base rent is required");

        if (baseRent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Base rent cannot be negative");
        }

        if (securityDeposit != null && securityDeposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Security deposit cannot be negative");
        }

        if (maintenanceFee != null && maintenanceFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Maintenance fee cannot be negative");
        }

        baseRent = normalize(baseRent);
        securityDeposit = normalize(securityDeposit);
        maintenanceFee = normalize(maintenanceFee);
    }

    // --------------------------------------------------
    // DOMAIN HELPERS
    // --------------------------------------------------

    public BigDecimal totalUpfrontCost() {

        return baseRent
                .add(defaultZero(securityDeposit))
                .add(defaultZero(maintenanceFee));
    }

    // --------------------------------------------------
    // NORMALIZATION
    // --------------------------------------------------

    private static BigDecimal normalize(BigDecimal value) {

        if (value == null) return null;

        return value.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private static BigDecimal defaultZero(BigDecimal value) {

        return value == null ? BigDecimal.ZERO : value;
    }
}