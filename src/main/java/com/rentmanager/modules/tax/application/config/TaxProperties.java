package com.rentmanager.modules.tax.application.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Feature toggles for the KRA tax pipeline (Phase 1 foundation).
 *
 * <p>All default to safe values: invoices and monthly filings are enabled
 * (they only create local records — nothing is transmitted yet), while the
 * VAT 16% branch is gated behind {@code vatBranchEnabled} which must stay
 * FALSE until the tax-advisor sign-off (brief item A1) completes.
 */
@Getter
@Component
@ConfigurationProperties(prefix = "app.tax")
public class TaxProperties {

    /** Master switch: generates tax invoices on rent payments. */
    private boolean invoiceEnabled = true;

    /** Master switch: computes monthly rental income filings. */
    private boolean filingEnabled = true;

    /**
     * 16% VAT branch for VAT-registered landlords on COMMERCIAL premises.
     * MUST remain false until a tax advisor confirms the branch (A1).
     */
    private boolean vatBranchEnabled = false;

    /** Max transmission attempts before an invoice/filing is parked. */
    private int maxAttempts = 3;

    /** Max invoices picked per transmission sweep pass. */
    private int transmissionBatchSize = 25;

    public void setInvoiceEnabled(boolean invoiceEnabled) { this.invoiceEnabled = invoiceEnabled; }
    public void setFilingEnabled(boolean filingEnabled) { this.filingEnabled = filingEnabled; }
    public void setVatBranchEnabled(boolean vatBranchEnabled) { this.vatBranchEnabled = vatBranchEnabled; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public void setTransmissionBatchSize(int transmissionBatchSize) { this.transmissionBatchSize = transmissionBatchSize; }
}
