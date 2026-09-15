package com.rentmanager.modules.tax.api.dto;

import com.rentmanager.modules.tax.domain.enums.PropertyTaxRegistrationStatus;
import com.rentmanager.modules.tax.domain.model.PropertyTaxRegistration;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record PropertyTaxRegistrationResponse(
        UUID id,
        UUID propertyId,
        PropertyTaxRegistrationStatus status,
        String krPropertyRegistrationId,
        LocalDateTime registeredAt,
        String lastError,
        Instant createdAt
) {

    public static PropertyTaxRegistrationResponse from(PropertyTaxRegistration reg) {
        return new PropertyTaxRegistrationResponse(
                reg.getId(),
                reg.getPropertyId(),
                reg.getStatus(),
                reg.getKrPropertyRegistrationId(),
                reg.getRegisteredAt(),
                reg.getLastError(),
                reg.getCreatedAt()
        );
    }
}
