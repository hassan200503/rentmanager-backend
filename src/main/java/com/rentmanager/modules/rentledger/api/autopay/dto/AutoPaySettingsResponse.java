package com.rentmanager.modules.rentledger.api.autopay.dto;

import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AutoPaySettingsResponse(
        UUID id,
        UUID leaseId,
        UUID tenantProfileId,
        boolean enabled,
        String mpesaPhone,
        LocalDate lastAutoPayDate,
        int consecutiveFailures,
        LocalDateTime lastAttemptAt,
        String lastFailureReason
) {
    public static AutoPaySettingsResponse from(AutoPaySettings settings) {
        return new AutoPaySettingsResponse(
                settings.getId(),
                settings.getLeaseId(),
                settings.getTenantProfileId(),
                settings.isEnabled(),
                settings.getMpesaPhone(),
                settings.getLastAutoPayDate(),
                settings.getConsecutiveFailures(),
                settings.getLastAttemptAt(),
                settings.getLastFailureReason()
        );
    }
}