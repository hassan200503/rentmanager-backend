package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Persistence twin of the {@code SubscriptionPaymentRequest} aggregate.
 * Tenant id is a plain column (like the domain model, which extends
 * BaseEntity rather than BaseTenantEntity) - tenant isolation at the
 * repository layer is enforced by the caller lookup patterns; the
 * migration adds the FK + the NULL-safe partial unique index on
 * checkout request id (which @UniqueConstraint cannot express).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "subscription_payment_requests",
        indexes = {
                @Index(name = "idx_sub_payment_requests_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sub_payment_requests_status", columnList = "status"),
                @Index(name = "idx_sub_payment_requests_created_at", columnList = "created_at")
        }
)
public class SubscriptionPaymentRequestJpaEntity extends BaseEntity {

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
}
