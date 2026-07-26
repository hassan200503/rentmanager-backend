package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "unmatched_payments")
public class UnmatchedPaymentJpaEntity extends BaseTenantEntity {

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(name = "account_reference")
    private String accountReference;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "mpesa_checkout_request_id")
    private String mpesaCheckoutRequestId;

    @Column(name = "mpesa_receipt_number", length = 50)
    private String mpesaReceiptNumber;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(name = "result_desc", length = 500)
    private String resultDesc;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "resolved_unit_id")
    private UUID resolvedUnitId;
}
