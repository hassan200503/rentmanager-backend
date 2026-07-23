package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RentPaymentRequestPersistenceMapper {

    public RentPaymentRequestJpaEntity toJpaEntity(RentPaymentRequest request) {
        if (request == null) return null;

        RentPaymentRequestJpaEntity jpa = new RentPaymentRequestJpaEntity();
        jpa.setId(request.getId());
        jpa.assignTenantIfUnset(request.getTenantId());
        jpa.setVersion(request.getVersion());
        jpa.setLeaseId(request.getLeaseId());
        jpa.setRentLedgerEntryId(request.getRentLedgerEntryId());
        jpa.setAmount(request.getAmount());
        jpa.setMpesaCheckoutRequestId(request.getMpesaCheckoutRequestId());
        jpa.setStatus(request.getStatus());
        jpa.setMpesaReceiptNumber(request.getMpesaReceiptNumber());
        return jpa;
    }

    public RentPaymentRequest toDomain(RentPaymentRequestJpaEntity jpa) {
        if (jpa == null) return null;

        return RentPaymentRequest.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getLeaseId(),
                jpa.getRentLedgerEntryId(),
                jpa.getAmount(),
                jpa.getMpesaCheckoutRequestId(),
                jpa.getStatus(),
                jpa.getMpesaReceiptNumber(),
                jpa.getCreatedAt(),
                jpa.getVersion()
        );
    }
}