package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.UnmatchedPayment;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.UnmatchedPaymentJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class UnmatchedPaymentPersistenceMapper {

    public UnmatchedPaymentJpaEntity toJpaEntity(UnmatchedPayment payment) {
        if (payment == null) return null;

        UnmatchedPaymentJpaEntity jpa = new UnmatchedPaymentJpaEntity();
        jpa.setId(payment.getId());
        jpa.assignTenantIfUnset(payment.getTenantId());
        jpa.setVersion(payment.getVersion());
        jpa.setTransactionId(payment.getTransactionId());
        jpa.setAmount(payment.getAmount());
        jpa.setPhoneNumber(payment.getPhoneNumber());
        jpa.setAccountReference(payment.getAccountReference());
        jpa.setOccurredAt(payment.getOccurredAt());
        jpa.setMpesaCheckoutRequestId(payment.getMpesaCheckoutRequestId());
        jpa.setMpesaReceiptNumber(payment.getMpesaReceiptNumber());
        jpa.setResultCode(payment.getResultCode());
        jpa.setResultDesc(payment.getResultDesc());
        jpa.setResolved(payment.isResolved());
        jpa.setResolvedAt(payment.getResolvedAt());
        jpa.setResolvedBy(payment.getResolvedBy());
        jpa.setResolvedUnitId(payment.getResolvedUnitId());
        return jpa;
    }

    public UnmatchedPayment toDomain(UnmatchedPaymentJpaEntity jpa) {
        if (jpa == null) return null;

        return UnmatchedPayment.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getTransactionId(),
                jpa.getAmount(),
                jpa.getPhoneNumber(),
                jpa.getAccountReference(),
                jpa.getOccurredAt(),
                jpa.getMpesaCheckoutRequestId(),
                jpa.getMpesaReceiptNumber(),
                jpa.getResultCode(),
                jpa.getResultDesc(),
                jpa.isResolved(),
                jpa.getResolvedAt(),
                jpa.getResolvedBy(),
                jpa.getResolvedUnitId(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}
