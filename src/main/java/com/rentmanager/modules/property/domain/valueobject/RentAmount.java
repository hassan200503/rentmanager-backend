package com.rentmanager.modules.property.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentAmount {

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    // --------------------------------------------------
    // FACTORY METHOD (SAFE CONSTRUCTION)
    // --------------------------------------------------

    public static RentAmount of(BigDecimal amount, String currency) {

        validate(amount, currency);

        return RentAmount.builder()
                .amount(amount)
                .currency(currency.trim().toUpperCase())
                .build();
    }

    // --------------------------------------------------
    // DOMAIN LOGIC
    // --------------------------------------------------

    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    private static void validate(BigDecimal amount, String currency) {

        if (amount == null) {
            throw new IllegalArgumentException("Rent amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Rent amount cannot be negative");
        }

        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        if (currency.trim().length() != 3) {
            throw new IllegalArgumentException("Currency must be a 3-letter ISO code (e.g. KES, USD)");
        }
    }
}