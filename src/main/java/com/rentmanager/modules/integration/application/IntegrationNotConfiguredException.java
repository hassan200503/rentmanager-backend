package com.rentmanager.modules.integration.application;

/**
 * Thrown when a provider has no active, configured credential set and no
 * environment fallback. Call sites MUST handle this and degrade gracefully
 * (disable the feature, surface an owner-facing banner) — it must never
 * bubble into a user-facing 500.
 */
public class IntegrationNotConfiguredException extends RuntimeException {

    private final String providerKey;

    public IntegrationNotConfiguredException(String providerKey) {
        super(providerKey + " is not configured or not active");
        this.providerKey = providerKey;
    }

    public String getProviderKey() {
        return providerKey;
    }
}