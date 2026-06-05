package com.rentmanager.modules.lease.domain.valueobject;

import java.math.BigDecimal;
import java.util.Objects;

public class Money {

    private final BigDecimal amount;

    public Money(BigDecimal amount) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }

        this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money subtract(Money other) {

        BigDecimal result = this.amount.subtract(other.amount);

        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }

        return new Money(result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money)) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }
}