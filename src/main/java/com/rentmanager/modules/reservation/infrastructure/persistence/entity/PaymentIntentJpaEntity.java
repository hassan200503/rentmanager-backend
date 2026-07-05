package com.rentmanager.modules.reservation.infrastructure.persistence.entity;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "payment_intents")
public class PaymentIntentJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(nullable = false)
    private UUID propertyId;

    @Column(name = "form_data_json", nullable = false, columnDefinition = "TEXT")
    private String formDataJson;

    @Column(name = "mpesa_checkout_request_id")
    private String mpesaCheckoutRequestId;

    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentIntentStatus status;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    // Set once by Hibernate on INSERT, never touched again. Backs the
    // stale-intent sweep (PaymentIntentExpiryScheduler), which releases
    // units stuck in PENDING_PAYMENT when the M-Pesa callback never
    // arrives at all (as opposed to arriving and reporting failure, which
    // MpesaCallbackService already handles directly).
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}