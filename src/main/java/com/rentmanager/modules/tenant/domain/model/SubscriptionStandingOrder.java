package com.rentmanager.modules.tenant.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderFrequency;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Merchant-initiated M-Pesa Ratiba standing order (Daraja product
 * "Mpesa Ratiba", {@code createStandingOrderExternal}). The landlord
 * pre-authorizes a recurring monthly payment into our Paybill with their
 * tenant code as the account reference; each execution arrives as a C2B
 * confirmation and extends the premium period by one billing cycle.
 *
 * <p>Deliberately mirrors the {@code SubscriptionPaymentRequest} shape:
 * created PENDING_AUTHORIZATION before the Daraja call, matched back to
 * the inbound Ratiba creation callback via {@code ratibaResponseRefId},
 * then advanced to ACTIVE or FAILED.</p>
 */
@Getter
@Entity
@Table(
        name = "subscription_standing_orders",
        indexes = {
                @Index(name = "idx_standing_orders_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_standing_orders_status", columnList = "status"),
                @Index(name = "idx_standing_orders_response_ref_id", columnList = "ratiba_response_ref_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionStandingOrder extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The landlord's tenant code - the C2B account reference (max 12 chars). */
    @Column(name = "account_reference", nullable = false, length = 12)
    private String accountReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private StandingOrderFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StandingOrderStatus status;

    @Column(name = "ratiba_response_ref_id")
    private String ratibaResponseRefId;

    @Column(name = "ratiba_transaction_id", length = 50)
    private String ratibaTransactionId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "failure_reason")
    private String failureReason;

    private SubscriptionStandingOrder(
            UUID tenantId,
            String accountReference,
            BigDecimal amount,
            StandingOrderFrequency frequency,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validateTenantId(tenantId);
        validateAccountReference(accountReference);
        validateAmount(amount);
        validateFrequency(frequency);
        validateDateRange(startDate, endDate);

        this.tenantId = tenantId;
        this.accountReference = accountReference;
        this.amount = amount;
        this.frequency = frequency;
        this.status = StandingOrderStatus.PENDING_AUTHORIZATION;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static SubscriptionStandingOrder create(
            UUID tenantId,
            String accountReference,
            BigDecimal amount,
            StandingOrderFrequency frequency,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new SubscriptionStandingOrder(
                tenantId, accountReference, amount, frequency, startDate, endDate
        );
    }

    public static SubscriptionStandingOrder rehydrate(
            UUID id,
            Long version,
            Instant createdAt,
            UUID tenantId,
            String accountReference,
            BigDecimal amount,
            StandingOrderFrequency frequency,
            StandingOrderStatus status,
            String ratibaResponseRefId,
            String ratibaTransactionId,
            LocalDate startDate,
            LocalDate endDate,
            String failureReason
    ) {
        SubscriptionStandingOrder order = new SubscriptionStandingOrder(
                tenantId, accountReference, amount, frequency, startDate, endDate
        );
        order.setId(id);
        order.setVersion(version);
        order.status = status;
        order.ratibaResponseRefId = ratibaResponseRefId;
        order.ratibaTransactionId = ratibaTransactionId;
        order.failureReason = failureReason;
        return order;
    }

    // ----------------------------------------------------------------
    // LIFECYCLE
    // ----------------------------------------------------------------

    public void attachResponseRefId(String responseRefId) {
        if (responseRefId == null || responseRefId.isBlank()) {
            throw new IllegalArgumentException("Ratiba response ref ID cannot be blank");
        }
        this.ratibaResponseRefId = responseRefId;
    }

    public void markActive(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("Ratiba transaction ID cannot be blank");
        }
        this.status = StandingOrderStatus.ACTIVE;
        this.ratibaTransactionId = transactionId;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = StandingOrderStatus.FAILED;
        this.failureReason = reason;
    }

    // ----------------------------------------------------------------
    // VALIDATION
    // ----------------------------------------------------------------

    private static void validateTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
    }

    private static void validateAccountReference(String accountReference) {
        if (accountReference == null || accountReference.isBlank()) {
            throw new IllegalArgumentException("Account reference cannot be blank");
        }
        if (accountReference.length() > 12) {
            throw new IllegalArgumentException(
                    "Account reference must be at most 12 characters (Daraja Ratiba limit)");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Standing order amount cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Standing order amount must be > 0");
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Standing order amount must be a whole number of shillings (Ratiba does not support decimals)");
        }
    }

    private static void validateFrequency(StandingOrderFrequency frequency) {
        if (frequency == null) {
            throw new IllegalArgumentException("Standing order frequency cannot be null");
        }
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Standing order start and end dates cannot be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Standing order end date cannot be before start date");
        }
    }
}
