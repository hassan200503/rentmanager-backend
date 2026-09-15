package com.rentmanager.modules.tax.api.dto;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TaxInvoiceResponse(
        UUID id,
        UUID rentTransactionId,
        UUID leaseId,
        BigDecimal amount,
        PremisesType premisesType,
        VatTreatment vatTreatment,
        TaxInvoiceStatus status,
        String externalReference,
        LocalDateTime occurredAt,
        String kraControlNumber,
        String qrCodeData,
        LocalDateTime transmittedAt,
        int attemptCount,
        String lastError
) {

    public static TaxInvoiceResponse from(TaxInvoice invoice) {
        return new TaxInvoiceResponse(
                invoice.getId(),
                invoice.getRentTransactionId(),
                invoice.getLeaseId(),
                invoice.getAmount(),
                invoice.getPremisesType(),
                invoice.getVatTreatment(),
                invoice.getStatus(),
                invoice.getExternalReference(),
                invoice.getOccurredAt(),
                invoice.getKraControlNumber(),
                invoice.getQrCodeData(),
                invoice.getTransmittedAt(),
                invoice.getAttemptCount(),
                invoice.getLastError()
        );
    }
}
