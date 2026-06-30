package com.rentmanager.modules.reservation.infrastructure.persistence.entity;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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
}