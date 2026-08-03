package com.rentmanager.modules.tax.application.dto;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payload handed to the eTIMS transmission port (KRA invoice submission).
 */
public record EimsInvoiceSubmission(
        UUID invoiceId,
        UUID tenantId,
        String landlordKraPin,
        String tenantKraPin,
        PremisesType premisesType,
        VatTreatment vatTreatment,
        BigDecimal amount,
        String externalReference,
        String source,
        LocalDateTime occurredAt
) {
}
