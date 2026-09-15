package com.rentmanager.modules.platformadmin.api.dto.response;

import java.math.BigDecimal;

/**
 * Read-model for {@code GET /api/v1/admin/overview}: platform-wide KPIs,
 * subscription funnel (trial → premium → lapsed), GMV, STK/B2C health,
 * and the environment (sandbox vs production) the backend is wired to.
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
            BigDecimal platformDefaultCommissionRate,
            // Subscription funnel: how many landlords are at each stage
            long trialLandlords,
            long premiumLandlords,
            long lapsedLandlords,
            // MRR: monthly_price sum for all active PREMIUM_MONTHLY tenants
            // (excludes Enterprise plans where monthly_price IS NULL)
            BigDecimal mrrAmount
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