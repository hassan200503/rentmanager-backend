package com.rentmanager.modules.tax.domain.enums;

/**
 * Lifecycle of an eTIMS tax invoice.
 *
 * <p>PENDING — generated, awaiting transmission (scheduled for the first
 * attempt). TRANSMITTED — accepted by KRA's eTIMS with a control number /
 * QR. FAILED — transmission attempt(s) failed; re-attempted by the sweeper
 * while {@code attemptCount < max} and {@code nextAttemptAt} is set.
 * SELF_FILED — the landlord filed this invoice themselves (manual bulk
 * filing path); never auto-retried.
 */
public enum TaxInvoiceStatus {

    PENDING,
    TRANSMITTED,
    FAILED,
    SELF_FILED
}
