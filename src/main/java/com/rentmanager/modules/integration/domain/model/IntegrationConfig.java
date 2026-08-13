package com.rentmanager.modules.integration.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain aggregate: the encrypted, per-environment credential record for one
 * provider. Owned and edited exclusively by the platform owner through the
 * Integrations console.
 */
public class IntegrationConfig {

    private final UUID id;
    private final String providerKey;
    private final IntegrationEnvironment environment;
    private boolean active;
    private String encryptedCredentials; // base64(iv || ciphertext || tag)
    private int keyVersion;
    private IntegrationStatus status;
    private Instant lastVerifiedAt;
    private String lastVerifiedBy;
    private String lastError;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    public IntegrationConfig(
            UUID id,
            String providerKey,
            IntegrationEnvironment environment,
            boolean active,
            String encryptedCredentials,
            int keyVersion,
            IntegrationStatus status,
            Instant lastVerifiedAt,
            String lastVerifiedBy,
            String lastError,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this.id = id;
        this.providerKey = providerKey;
        this.environment = environment;
        this.active = active;
        this.encryptedCredentials = encryptedCredentials;
        this.keyVersion = keyVersion;
        this.status = status;
        this.lastVerifiedAt = lastVerifiedAt;
        this.lastVerifiedBy = lastVerifiedBy;
        this.lastError = lastError;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static IntegrationConfig newUnconfigured(String providerKey, IntegrationEnvironment environment) {
        return new IntegrationConfig(
                UUID.randomUUID(),
                providerKey,
                environment,
                false,
                "",
                1,
                IntegrationStatus.NOT_CONFIGURED,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now(),
                0L
        );
    }

    public void replaceCredentials(String encrypted, int keyVersion, String actor) {
        this.encryptedCredentials = encrypted;
        this.keyVersion = keyVersion;
        this.updatedBy = actor;
        // Any credential change invalidates the previous verification.
        this.status = IntegrationStatus.CONFIGURED;
        this.lastError = null;
    }

    public void markVerified(String actor) {
        this.status = IntegrationStatus.VERIFIED;
        this.lastVerifiedAt = Instant.now();
        this.lastVerifiedBy = actor;
        this.lastError = null;
    }

    public void markError(String error) {
        this.status = IntegrationStatus.ERROR;
        this.lastError = error == null ? null : truncate(error, 2000);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public IntegrationEnvironment getEnvironment() {
        return environment;
    }

    public boolean isActive() {
        return active;
    }

    public String getEncryptedCredentials() {
        return encryptedCredentials;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public IntegrationStatus getStatus() {
        return status;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public String getLastVerifiedBy() {
        return lastVerifiedBy;
    }

    public String getLastError() {
        return lastError;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Whether the record holds any encrypted secret payload at all.
     */
    public boolean hasCredentials() {
        return encryptedCredentials != null && !encryptedCredentials.isBlank();
    }

    /**
     * Whether the row is a fully usable saved configuration. A config that
     * stores all fields as empty strings is treated as unconfigured.
     */
    public boolean configured(Map<String, String> decrypted) {
        return hasCredentials() && decrypted.values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}