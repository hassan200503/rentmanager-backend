package com.rentmanager.modules.tax.domain.enums;

/**
 * Lifecycle of a landlord's Monthly Rental Income filing (eRITS).
 *
 * <p>COMPUTED — the monthly position (gross income, rate, tax due) was
 * computed and persisted; nothing transmitted yet. READY_FOR_MANUAL — the
 * landlord chose to file manually. TRANSMITTED — submitted to eRITS.
 * FAILED — submission failed; re-attempted by the sweeper while retry
 * bookkeeping allows.
 */
public enum MonthlyFilingStatus {

    COMPUTED,
    READY_FOR_MANUAL,
    TRANSMITTED,
    FAILED
}
