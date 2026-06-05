package com.rentmanager.modules.unit.domain.valueobject;

import java.util.Objects;

public record UnitFeature(
        String name,
        String value
) {

    public UnitFeature {

        Objects.requireNonNull(name, "Feature name is required");
        Objects.requireNonNull(value, "Feature value is required");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Feature name cannot be blank");
        }

        if (value.isBlank()) {
            throw new IllegalArgumentException("Feature value cannot be blank");
        }

        name = normalize(name);
        value = value.trim();
    }

    // --------------------------------------------------
    // FACTORY METHOD (OPTIONAL BUT CLEANER)
    // --------------------------------------------------

    public static UnitFeature of(String name, String value) {
        return new UnitFeature(name, value);
    }

    // --------------------------------------------------
    // NORMALIZATION
    // --------------------------------------------------

    private static String normalize(String input) {

        String cleaned = input.trim().toLowerCase();

        return switch (cleaned) {
            case "wifi", "wi-fi", "wireless internet" -> "WiFi";
            case "ac", "air conditioning" -> "Air Conditioning";
            case "parking space", "parking" -> "Parking";
            default -> capitalize(cleaned);
        };
    }

    private static String capitalize(String input) {

        if (input.isEmpty()) return input;

        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}