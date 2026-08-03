package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.tax.domain.enums.PropertyTaxRegistrationStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * eRITS property-level registration record for a landlord.
 *
 * <p>One row per (tenant, property). The landlord PIN (and optionally the
 * eRITS-expected tenant PIN) is denormalised at initiation so the
 * registration survives later tenant edits. Registration is per property;
 * whether KRA additionally expects per-unit (room) identifiers is an open
 * question for KRA's schema (Phase 3, item A1).
 */
public class PropertyTaxRegistration extends AggregateRoot {

    private UUID propertyId;
    private String landlordKraPin;
    private String tenantKraPin;
    private String krPropertyRegistrationId;
    private PropertyTaxRegistrationStatus status;
    private LocalDateTime registeredAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    protected PropertyTaxRegistration() {
    }

    public static PropertyTaxRegistration initiate(
            UUID landlordTenantId,
            UUID propertyId,
            String landlordKraPin,
            String tenantKraPin
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (propertyId == null) {
            throw new IllegalArgumentException("propertyId is required");
        }

        PropertyTaxRegistration registration = new PropertyTaxRegistration();
        registration.setId(UUID.randomUUID());
        registration.assignTenant(landlordTenantId);
        registration.propertyId = propertyId;
        registration.landlordKraPin = landlordKraPin;
        registration.tenantKraPin = tenantKraPin;
        registration.status = PropertyTaxRegistrationStatus.PENDING;
        return registration;
    }

    public void markTransmitted() {
        if (status == PropertyTaxRegistrationStatus.PENDING) {
            this.status = PropertyTaxRegistrationStatus.TRANSMITTED;
        }
    }

    public void markAccepted(String krPropertyRegistrationId) {
        this.krPropertyRegistrationId = krPropertyRegistrationId;
        this.status = PropertyTaxRegistrationStatus.ACCEPTED;
        this.registeredAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markRejected(String error) {
        this.status = PropertyTaxRegistrationStatus.REJECTED;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.registeredAt = null;
    }

    /**
     * Hands the registration over to the landlord for manual filing (no
     * auto-re-transmission).
     */
    public void markReadyForManual() {
        this.status = PropertyTaxRegistrationStatus.READY_FOR_MANUAL;
    }

    public static PropertyTaxRegistration rehydrate(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            String landlordKraPin,
            String tenantKraPin,
            String krPropertyRegistrationId,
            PropertyTaxRegistrationStatus status,
            LocalDateTime registeredAt,
            String lastError,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        PropertyTaxRegistration registration = new PropertyTaxRegistration();
        registration.setId(id);
        registration.restoreTenantId(tenantId);
        registration.propertyId = propertyId;
        registration.landlordKraPin = landlordKraPin;
        registration.tenantKraPin = tenantKraPin;
        registration.krPropertyRegistrationId = krPropertyRegistrationId;
        registration.status = status;
        registration.registeredAt = registeredAt;
        registration.lastError = lastError;
        registration.version = version;
        registration.createdAt = createdAt;
        registration.updatedAt = updatedAt;
        return registration;
    }

    public UUID getPropertyId() { return propertyId; }
    public String getLandlordKraPin() { return landlordKraPin; }
    public String getTenantKraPin() { return tenantKraPin; }
    public String getKrPropertyRegistrationId() { return krPropertyRegistrationId; }
    public PropertyTaxRegistrationStatus getStatus() { return status; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}