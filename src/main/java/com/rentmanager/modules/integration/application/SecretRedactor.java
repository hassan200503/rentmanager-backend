package com.rentmanager.modules.integration.application;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Masks secret-shaped values everywhere they could leak: API responses,
 * audit metadata and log lines. The same redaction is applied by every
 * integration write path — never log raw credential material.
 */
public final class SecretRedactor {

    private SecretRedactor() {
    }

    /**
     * {@code sk_live_••••••••42a1} style preview — last 4 characters plus a
     * fixed mask. Short values collapse to a full mask.
     */
    public static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 6) {
            return "••••••";
        }
        return "••••••••" + value.substring(value.length() - 4);
    }

    /**
     * Builds a redacted, field-level diff for the audit log: each secret
     * field becomes {@code <redacted>} plus a changed/unchanged marker.
     * Non-secret fields keep their values (previews are harmless).
     */
    public static Map<String, Object> redactedDiff(
            Map<String, String> before,
            Map<String, String> after,
            java.util.function.Predicate<String> isSecretField
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String oldValue = before.get(key);
            String newValue = after.get(key);
            boolean changed = !java.util.Objects.equals(oldValue, newValue);
            if (isSecretField.test(key)) {
                merged.put(key, changed ? "<redacted:changed>" : "<redacted:unchanged>");
            } else if (changed) {
                merged.put(key, newValue == null ? "" : newValue);
            }
        }
        return merged;
    }
}