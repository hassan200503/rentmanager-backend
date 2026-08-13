package com.rentmanager.modules.integration.domain.model;

/**
 * One configurable field of a provider. Secret fields are masked in every
 * API response and their values are never echoed anywhere.
 */
public record ProviderField(
        String key,
        String label,
        boolean secret,
        String placeholder
) {
    public static ProviderField secret(String key, String label, String placeholder) {
        return new ProviderField(key, label, true, placeholder);
    }

    public static ProviderField plain(String key, String label, String placeholder) {
        return new ProviderField(key, label, false, placeholder);
    }
}