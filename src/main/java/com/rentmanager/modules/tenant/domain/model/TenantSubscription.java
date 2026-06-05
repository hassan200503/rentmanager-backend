package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenant_subscriptions",
        indexes = {
                @Index(
                        name = "idx_subscription_tenant_id",
                        columnList = "tenant_id"
                ),
                @Index(
                        name = "idx_subscription_plan_id",
                        columnList = "subscription_plan_id"
                ),
                @Index(
                        name = "idx_subscription_status",
                        columnList = "status"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantSubscription extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_plan_id", nullable = false)
    private UUID subscriptionPlanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private SubscriptionStatus status;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "external_billing_reference", length = 255)
    private String externalBillingReference;

    private TenantSubscription(
            UUID tenantId,
            UUID subscriptionPlanId,
            SubscriptionStatus status,
            LocalDate startDate,
            LocalDate endDate,
            boolean autoRenew
    ) {

        validateTenantId(tenantId);
        validateSubscriptionPlanId(subscriptionPlanId);
        validateStatus(status);
        validateStartDate(startDate);

        this.tenantId = tenantId;
        this.subscriptionPlanId = subscriptionPlanId;
        this.status = status;
        this.startDate = startDate;
        this.endDate = endDate;
        this.autoRenew = autoRenew;
    }

    public static TenantSubscription createTrialSubscription(
            UUID tenantId,
            UUID subscriptionPlanId,
            LocalDate startDate,
            LocalDate trialEndDate
    ) {

        if (trialEndDate == null) {
            throw new IllegalArgumentException(
                    "Trial end date cannot be null"
            );
        }

        if (trialEndDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Trial end date cannot be before start date"
            );
        }

        TenantSubscription subscription = new TenantSubscription(
                tenantId,
                subscriptionPlanId,
                SubscriptionStatus.TRIAL,
                startDate,
                null,
                false
        );

        subscription.trialEndDate = trialEndDate;

        return subscription;
    }

    public static TenantSubscription createActiveSubscription(
            UUID tenantId,
            UUID subscriptionPlanId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal amount,
            boolean autoRenew
    ) {

        validateAmount(amount);

        if (endDate == null) {
            throw new IllegalArgumentException(
                    "Subscription end date cannot be null"
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException(
                    "Subscription end date cannot be before start date"
            );
        }

        TenantSubscription subscription = new TenantSubscription(
                tenantId,
                subscriptionPlanId,
                SubscriptionStatus.ACTIVE,
                startDate,
                endDate,
                autoRenew
        );

        subscription.amount = amount;

        return subscription;
    }

    // ----------------------------------------------------------------
    // LIFECYCLE OPERATIONS
    // ----------------------------------------------------------------

    public void activate() {

        if (this.status == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled subscription cannot be reactivated"
            );
        }

        if (this.status == SubscriptionStatus.EXPIRED) {
            throw new IllegalStateException(
                    "Expired subscription cannot be reactivated"
            );
        }

        if (this.status == SubscriptionStatus.ACTIVE) {
            return;
        }

        this.status = SubscriptionStatus.ACTIVE;
    }

    public void expire() {

        if (this.status == SubscriptionStatus.EXPIRED) {
            return;
        }

        this.status = SubscriptionStatus.EXPIRED;
        this.autoRenew = false;
    }

    public void cancel() {

        if (this.status == SubscriptionStatus.CANCELLED) {
            return;
        }

        this.status = SubscriptionStatus.CANCELLED;
        this.autoRenew = false;
    }

    public void markPastDue() {

        if (this.status == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException(
                    "Cancelled subscription cannot become past due"
            );
        }

        this.status = SubscriptionStatus.PAST_DUE;
    }

    // ----------------------------------------------------------------
    // BILLING
    // ----------------------------------------------------------------

    public void attachBillingReference(String billingReference) {

        if (billingReference == null || billingReference.isBlank()) {
            throw new IllegalArgumentException(
                    "Billing reference cannot be blank"
            );
        }

        this.externalBillingReference = billingReference;
    }

    // ----------------------------------------------------------------
    // STATUS HELPERS
    // ----------------------------------------------------------------

    public boolean isActive() {
        return SubscriptionStatus.ACTIVE.equals(this.status);
    }

    public boolean isTrial() {
        return SubscriptionStatus.TRIAL.equals(this.status);
    }

    public boolean isExpired() {

        return this.endDate != null
                && this.endDate.isBefore(LocalDate.now());
    }

    // ----------------------------------------------------------------
    // VALIDATION
    // ----------------------------------------------------------------

    private static void validateTenantId(UUID tenantId) {

        if (tenantId == null) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be null"
            );
        }
    }

    private static void validateSubscriptionPlanId(
            UUID subscriptionPlanId
    ) {

        if (subscriptionPlanId == null) {
            throw new IllegalArgumentException(
                    "Subscription plan ID cannot be null"
            );
        }
    }

    private static void validateStatus(
            SubscriptionStatus status
    ) {

        if (status == null) {
            throw new IllegalArgumentException(
                    "Subscription status cannot be null"
            );
        }
    }

    private static void validateStartDate(LocalDate startDate) {

        if (startDate == null) {
            throw new IllegalArgumentException(
                    "Subscription start date cannot be null"
            );
        }
    }

    private static void validateAmount(BigDecimal amount) {

        if (amount == null) {
            throw new IllegalArgumentException(
                    "Subscription amount cannot be null"
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Subscription amount cannot be negative"
            );
        }
    }
}