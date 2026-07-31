package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
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
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a single M-Pesa STK push for the subscription billing flow
 * (initial activation or monthly renewal). Deliberately mirrors the
 * shape of {@code RentPaymentRequest}: created before the STK push so the
 * checkout id can be attached afterwards, matched back to the inbound
 * Daraja callback via {@code mpesaCheckoutRequestId}.
 */
@Getter
@Entity
@Table(
        name = "subscription_payment_requests",
        indexes = {
                @Index(name = "idx_sub_payment_requests_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sub_payment_requests_status", columnList = "status"),
                @Index(name = "idx_sub_payment_requests_created_at", columnList = "created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionPaymentRequest extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "subscription_plan_id", nullable = false)
    private UUID subscriptionPlanId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "mpesa_phone", nullable = false, length = 20)
    private String mpesaPhone;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private SubscriptionPaymentPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SubscriptionPaymentRequestStatus status;

    @Column(name = "mpesa_checkout_request_id")
    private String mpesaCheckoutRequestId;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    @Column(name = "failure_reason")
    private String failureReason;

    private SubscriptionPaymentRequest(
            UUID tenantId,
            UUID subscriptionPlanId,
            BigDecimal amount,
            String mpesaPhone,
            SubscriptionPaymentPurpose purpose
    ) {
        validateTenantId(tenantId);
        validatePlanId(subscriptionPlanId);
        validateAmount(amount);
        validateMpesaPhone(mpesaPhone);
        validatePurpose(purpose);

        this.tenantId = tenantId;
        this.subscriptionPlanId = subscriptionPlanId;
        this.amount = amount;
        this.mpesaPhone = mpesaPhone;
        this.purpose = purpose;
        this.status = SubscriptionPaymentRequestStatus.PENDING;
    }

    public static SubscriptionPaymentRequest create(
            UUID tenantId,
            UUID subscriptionPlanId,
            BigDecimal amount,
            String mpesaPhone,
            SubscriptionPaymentPurpose purpose
    ) {
        return new SubscriptionPaymentRequest(
                tenantId, subscriptionPlanId, amount, mpesaPhone, purpose
        );
    }

    public static SubscriptionPaymentRequest rehydrate(
            UUID id,
            Long version,
            Instant createdAt,
            UUID tenantId,
            UUID subscriptionPlanId,
            BigDecimal amount,
            String mpesaPhone,
            SubscriptionPaymentPurpose purpose,
            SubscriptionPaymentRequestStatus status,
            String mpesaCheckoutRequestId,
            String mpesaReceiptNumber,
            String failureReason
    ) {
        SubscriptionPaymentRequest request = new SubscriptionPaymentRequest(
                tenantId, subscriptionPlanId, amount, mpesaPhone, purpose
        );
        request.setId(id);
        request.setVersion(version);
        request.restoreCreatedAt(createdAt);
        request.status = status;
        request.mpesaCheckoutRequestId = mpesaCheckoutRequestId;
        request.mpesaReceiptNumber = mpesaReceiptNumber;
        request.failureReason = failureReason;
        return request;
    }

    // ----------------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------------

    public void attachCheckoutRequestId(String checkoutRequestId) {
        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            throw new IllegalArgumentException("Checkout request ID cannot be blank");
        }
        this.mpesaCheckoutRequestId = checkoutRequestId;
    }

    public void markPaid(String mpesaReceiptNumber) {
        if (mpesaReceiptNumber == null || mpesaReceiptNumber.isBlank()) {
            throw new IllegalArgumentException("M-Pesa receipt number cannot be blank");
        }
        if (this.status == SubscriptionPaymentRequestStatus.PAID) {
            return;
        }
        this.status = SubscriptionPaymentRequestStatus.PAID;
        this.mpesaReceiptNumber = mpesaReceiptNumber;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        if (this.status == SubscriptionPaymentRequestStatus.PAID) {
            throw new IllegalStateException("Paid request cannot be marked failed");
        }
        this.status = SubscriptionPaymentRequestStatus.FAILED;
        this.failureReason = reason;
    }

    public void markExpired(String reason) {
        if (this.status == SubscriptionPaymentRequestStatus.PAID) {
            throw new IllegalStateException("Paid request cannot be marked expired");
        }
        this.status = SubscriptionPaymentRequestStatus.EXPIRED;
        this.failureReason = reason;
    }

    public boolean isTerminalFailure() {
        return this.status == SubscriptionPaymentRequestStatus.FAILED
                || this.status == SubscriptionPaymentRequestStatus.EXPIRED;
    }

    public boolean isRenewal() {
        return this.purpose == SubscriptionPaymentPurpose.RENEWAL;
    }

    // ----------------------------------------------------------------
    // VALIDATION
    // ----------------------------------------------------------------

    private static void validateTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
    }

    private static void validatePlanId(UUID subscriptionPlanId) {
        if (subscriptionPlanId == null) {
            throw new IllegalArgumentException("Subscription plan ID cannot be null");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Subscription payment amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Subscription payment amount must be > 0");
        }
    }

    private static void validateMpesaPhone(String mpesaPhone) {
        if (mpesaPhone == null || mpesaPhone.isBlank()) {
            throw new IllegalArgumentException("M-Pesa phone number cannot be blank");
        }
    }

    private static void validatePurpose(SubscriptionPaymentPurpose purpose) {
        if (purpose == null) {
            throw new IllegalArgumentException("Subscription payment purpose cannot be null");
        }
    }
}
