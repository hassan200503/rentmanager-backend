package com.rentmanager.modules.tax.domain.enums;

/**
 * Lifecycle of a row in the landlord Monthly Rental Income rate schedule.
 *
 * <p>Only the single ACTIVE row (the latest effective_from) is ever read
 * for computation. SCHEDULED rows are pre-staged rates (e.g. an
 * unverified Finance Act change) that must be human-verified before being
 * activated — they are NEVER used in computation. SUPERSEDED rows are
 * closed-off historical rates (effective_to set), kept for audit.
 */
public enum MriRateScheduleStatus {

    ACTIVE,
    SCHEDULED,
    SUPERSEDED
}
