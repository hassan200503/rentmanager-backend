package com.rentmanager.modules.lease.domain.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

public class LeaseNumber {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z0-9\\-]{5,30}$");

    private final String value;

    public LeaseNumber(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Lease number cannot be blank");
        }

        String normalized = value.trim().toUpperCase();

        if (!PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Invalid lease number format"
            );
        }

        this.value = normalized;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeaseNumber)) return false;
        LeaseNumber that = (LeaseNumber) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}