package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnmatchedPayment extends AggregateRoot {

    private String transactionId;
    private BigDecimal amount;
    private String phoneNumber;
    private String accountReference;
    private LocalDateTime occurredAt;
    private String mpesaCheckoutRequestId;
    private String mpesaReceiptNumber;
    private Integer resultCode;
    private String resultDesc;
    private boolean resolved;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private UUID resolvedUnitId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    public static UnmatchedPayment create(
            UUID tenantId,
            String transactionId,
            BigDecimal amount,
            String phoneNumber,
            String accountReference,
            LocalDateTime occurredAt,
            String mpesaCheckoutRequestId,
            String mpesaReceiptNumber,
            Integer resultCode,
            String resultDesc
    ) {
        UnmatchedPayment payment = UnmatchedPayment.builder()
                .transactionId(transactionId)
                .amount(amount)
                .phoneNumber(phoneNumber)
                .accountReference(accountReference)
                .occurredAt(occurredAt)
                .mpesaCheckoutRequestId(mpesaCheckoutRequestId)
                .mpesaReceiptNumber(mpesaReceiptNumber)
                .resultCode(resultCode)
                .resultDesc(resultDesc)
                .resolved(false)
                .build();

        payment.setId(UUID.randomUUID());
        payment.assignTenant(tenantId);
        return payment;
    }

    public void resolve(UUID unitId, String resolvedBy) {
        this.resolved = true;
        this.resolvedUnitId = unitId;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }

    public static UnmatchedPayment rehydrate(
            UUID id,
            UUID tenantId,
            String transactionId,
            BigDecimal amount,
            String phoneNumber,
            String accountReference,
            LocalDateTime occurredAt,
            String mpesaCheckoutRequestId,
            String mpesaReceiptNumber,
            Integer resultCode,
            String resultDesc,
            boolean resolved,
            LocalDateTime resolvedAt,
            String resolvedBy,
            UUID resolvedUnitId,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        UnmatchedPayment payment = UnmatchedPayment.builder()
                .transactionId(transactionId)
                .amount(amount)
                .phoneNumber(phoneNumber)
                .accountReference(accountReference)
                .occurredAt(occurredAt)
                .mpesaCheckoutRequestId(mpesaCheckoutRequestId)
                .mpesaReceiptNumber(mpesaReceiptNumber)
                .resultCode(resultCode)
                .resultDesc(resultDesc)
                .resolved(resolved)
                .resolvedAt(resolvedAt)
                .resolvedBy(resolvedBy)
                .resolvedUnitId(resolvedUnitId)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        payment.setId(id);
        payment.assignTenant(tenantId);
        return payment;
    }
}
