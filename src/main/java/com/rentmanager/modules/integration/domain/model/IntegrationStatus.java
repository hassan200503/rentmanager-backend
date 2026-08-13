package com.rentmanager.modules.integration.domain.model;

/**
 * Lifecycle of one environment's credential record:
 * NOT_CONFIGURED - nothing saved yet (env-provided fallbacks may still apply)
 * CONFIGURED    - saved via the console, never verified against the provider
 * VERIFIED      - a real Test Connection passed since the last save
 * ERROR         - the last Test Connection failed (lastError carries the reason)
 */
public enum IntegrationStatus {
    NOT_CONFIGURED,
    CONFIGURED,
    VERIFIED,
    ERROR
}