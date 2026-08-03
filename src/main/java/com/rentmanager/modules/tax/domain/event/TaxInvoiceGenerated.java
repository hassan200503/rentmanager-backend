package com.rentmanager.modules.tax.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fired when a tax invoice is generated (persisted) for a rent
 * transaction. Consumed by the transmission pipeline (Phase 2/3) — the
 * event exists so a future transmission service can react without the
 * invoice-generation listener knowing about transmission details.
 */
public class TaxInvoiceGenerated extends DomainEvent {

    private final UUID transactionId;
    private final BigDecimal amount;
    private final String vatTreatment;

    public TaxInvoiceGenerated(
            UUID tenantId,
            UUID invoiceId,
            String correlationId,
            UUID transactionId,
            BigDecimal amount,
            String vatTreatment
    ) {
        super(tenantId, invoiceId, correlationId);
        this.transactionId = transactionId;
        this.amount = amount;
        this.vatTreatment = vatTreatment;
    }

    public UUID getTransactionId() { return transactionId; }
    public BigDecimal getAmount() { return amount; }
    public String getVatTreatment() { return vatTreatment; }

    @Override
    public String eventType() {
        return "TaxInvoiceGenerated";
    }
}
