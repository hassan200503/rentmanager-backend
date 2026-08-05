package com.rentmanager.modules.platformadmin.api.dto.response;

import java.math.BigDecimal;

/**
 * Read-model for {@code GET /api/v1/admin/overview}: platform-wide KPIs,
 * current/previous month collection + commission, STK and B2C health, and
 * the environment (sandbox vs production) the backend is wired to.
 *
 * All money values are retained commission amounts (commission never moves
 * physically — it stays in the collection shortcode), so "commission" here
 * is the platform's share on the month's completed rent transactions.
 */
public record AdminOverviewResponse(
        PlatformStats platform,
        PaymentStats payments,
        DisbursementStats disbursements,
        EnvironmentInfo environment
) {

    public record PlatformStats(
            long totalTenants,
            long activeTenants,
            long suspendedTenants,
            long pendingOnboardingTenants,
            long deactivatedTenants,
            long totalProperties,
            long totalUnits,
            long activeLeases,
            long totalRenters,
            BigDecimal platformDefaultCommissionRate
    ) {}

    public record PaymentStats(
            BigDecimal gmvCurrentMonth,
            BigDecimal gmvPreviousMonth,
            BigDecimal commissionCurrentMonth,
            BigDecimal commissionPreviousMonth,
            long paymentRequestsPending,
            long paymentRequestsPaid,
            long paymentRequestsFailed
    ) {}

    public record DisbursementStats(
            long initiated,
            long pending,
            long success,
            long failed,
            long requiresManualAttention
    ) {}

    public record EnvironmentInfo(String environment, boolean sandbox) {}
}