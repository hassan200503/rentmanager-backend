package com.rentmanager.modules.tenant.domain.enums;

/**
 * How a landlord is billed by the platform (Phase 1 dual revenue model).
 *
 * <ul>
 *   <li>{@code COMMISSION} - pay-as-you-go: a per-payment commission is
 *       deducted from each rent payment (rate from the active
 *       commission_policies row) before B2C disbursement. Default mode;
 *       zero behavior change to the existing payment pipeline.</li>
 *   <li>{@code PREMIUM_MONTHLY} - flat monthly fee per the active
 *       subscription plan tier; no commission is deducted from rent
 *       payments (100% net is disbursed to the landlord).</li>
 * </ul>
 *
 * Lives on the {@code Tenant} aggregate as the single source of truth for
 * revenue treatment at rent-payment time. Fail-closed: default COMMISSION.
 */
public enum BillingMode {
    COMMISSION,
    PREMIUM_MONTHLY
}
