package com.rentmanager.modules.tax.application.dto;

import java.util.UUID;

/**
 * Payload handed to the eRITS transmission port (property registration).
 */
public record ErisPropertyRegistrationSubmission(
        UUID registrationId,
        UUID tenantId,
        String landlordKraPin,
        String tenantKraPin,
        UUID propertyId
) {
}
