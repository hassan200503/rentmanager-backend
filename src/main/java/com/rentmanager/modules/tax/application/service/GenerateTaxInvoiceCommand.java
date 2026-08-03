package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import com.rentmanager.modules.tax.domain.model.TaxInvoice;
import com.rentmanager.modules.tax.domain.repository.TaxInvoiceRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Input for tax-invoice generation, assembled by the payment listener
 * from the rent ledger transaction and its related aggregates.
 */
public record GenerateTaxInvoiceCommand(
        UUID tenantId,
        UUID rentTransactionId,
        UUID ledgerEntryId,
        UUID leaseId,
        UUID tenantProfileId,
        String landlordKraPin,
        String tenantKraPin,
        PremisesType premisesType,
        VatTreatment vatTreatment,
        BigDecimal amount,
        String externalReference,
        String source,
        LocalDateTime occurredAt
) {
    /**
     * A residential PAYMENT transaction is never re-invoiceable — the
     * (tenant, transaction) unique key plus this check keeps event replay
     * and concurrent deliveries harmless.
     */
    public TaxInvoice toInvoice() {
        return TaxInvoice.create(
                tenantId,
                rentTransactionId,
                ledgerEntryId,
                leaseId,
                tenantProfileId,
                landlordKraPin,
                tenantKraPin,
                premisesType,
                vatTreatment,
                amount,
                externalReference,
                source,
                occurredAt
        );
    }
}
