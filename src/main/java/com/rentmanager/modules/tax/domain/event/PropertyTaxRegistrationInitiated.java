package com.rentmanager.modules.tax.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

/**
 * Fired when a property's eRITS registration is initiated. Consumed by
 * future phases (registration transmission, landlord onboarding flow).
 */
public class PropertyTaxRegistrationInitiated extends DomainEvent {

    private final UUID propertyId;

    public PropertyTaxRegistrationInitiated(
            UUID tenantId,
            UUID registrationId,
            String correlationId,
            UUID propertyId
    ) {
        super(tenantId, registrationId, correlationId);
        this.propertyId = propertyId;
    }

    public UUID getPropertyId() { return propertyId; }

    @Override
    public String eventType() {
        return "PropertyTaxRegistrationInitiated";
    }
}
