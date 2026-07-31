package com.rentmanager.modules.tenant.application.dto.response;

import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Current subscription-billing state for a landlord, including the
 * Ratiba onboarding details (Paybill number, account reference, fee) that
 * the dashboard renders for the standing-order setup, and whether
 * merchant-initiated Ratiba creation is available.
 */
public class SubscriptionStatusResponse {

    private final BillingMode billingMode;
    private final SubscriptionStatus subscriptionStatus;
    private final String planCode;
    private final String planName;
    private final BigDecimal planMonthlyPrice;
    private final LocalDate planStartDate;
    private final LocalDate planEndDate;
    private final LocalDate planGraceEndsAt;
    private final boolean planAutoRenew;
    private final String paybillNumber;
    private final String accountReference;
    private final boolean ratibaEnabled;
    private final StandingOrderStatus standingOrderStatus;

    public SubscriptionStatusResponse(
            BillingMode billingMode,
            SubscriptionStatus subscriptionStatus,
            String planCode,
            String planName,
            BigDecimal planMonthlyPrice,
            LocalDate planStartDate,
            LocalDate planEndDate,
            LocalDate planGraceEndsAt,
            boolean planAutoRenew,
            String paybillNumber,
            String accountReference,
            boolean ratibaEnabled,
            StandingOrderStatus standingOrderStatus
    ) {
        this.billingMode = billingMode;
        this.subscriptionStatus = subscriptionStatus;
        this.planCode = planCode;
        this.planName = planName;
        this.planMonthlyPrice = planMonthlyPrice;
        this.planStartDate = planStartDate;
        this.planEndDate = planEndDate;
        this.planGraceEndsAt = planGraceEndsAt;
        this.planAutoRenew = planAutoRenew;
        this.paybillNumber = paybillNumber;
        this.accountReference = accountReference;
        this.ratibaEnabled = ratibaEnabled;
        this.standingOrderStatus = standingOrderStatus;
    }

    public BillingMode getBillingMode() { return billingMode; }
    public SubscriptionStatus getSubscriptionStatus() { return subscriptionStatus; }
    public String getPlanCode() { return planCode; }
    public String getPlanName() { return planName; }
    public BigDecimal getPlanMonthlyPrice() { return planMonthlyPrice; }
    public LocalDate getPlanStartDate() { return planStartDate; }
    public LocalDate getPlanEndDate() { return planEndDate; }
    public LocalDate getPlanGraceEndsAt() { return planGraceEndsAt; }
    public boolean isPlanAutoRenew() { return planAutoRenew; }
    public String getPaybillNumber() { return paybillNumber; }
    public String getAccountReference() { return accountReference; }
    public boolean isRatibaEnabled() { return ratibaEnabled; }
    public StandingOrderStatus getStandingOrderStatus() { return standingOrderStatus; }
}
