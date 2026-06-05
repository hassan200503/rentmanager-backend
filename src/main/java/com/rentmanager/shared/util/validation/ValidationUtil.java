package com.rentmanager.shared.util.validation;

public final class ValidationUtil {

    private ValidationUtil() {
    }

    public static void requireNonBlank(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}