package com.rentmanager.shared.util.string;

public final class StringUtil {

    private StringUtil() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    public static String normalize(String value) {
        return isBlank(value)
                ? null
                : value.trim().replaceAll("\\s+", " ");
    }

    public static boolean equalsIgnoreCase(
            String first,
            String second
    ) {
        if (first == null || second == null) {
            return false;
        }

        return first.equalsIgnoreCase(second);
    }
}