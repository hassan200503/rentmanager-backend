package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPaymentRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPaymentRequestPersistenceMapper {

    public SubscriptionPaymentRequestJpaEntity toJpaEntity(SubscriptionPaymentRequest request) {
        if (request == null) return null;

        SubscriptionPaymentRequestJpaEntity jpa = new SubscriptionPaymentRequestJpaEntity();
        jpa.setId(request.getId());
        jpa.setVersion(request.getVersion());
        jpa.setTenantId(request.getTenantId());
        jpa.setSubscriptionPlanId(request.getSubscriptionPlanId());
        jpa.setAmount(request.getAmount());
        jpa.setMpesaPhone(request.getMpesaPhone());
        jpa.setPurpose(request.getPurpose());
        jpa.setStatus(request.getStatus());
        jpa.setMpesaCheckoutRequestId(request.getMpesaCheckoutRequestId());
        jpa.setMpesaReceiptNumber(request.getMpesaReceiptNumber());
        jpa.setFailureReason(request.getFailureReason());
        return jpa;
    }

    public SubscriptionPaymentRequest toDomain(SubscriptionPaymentRequestJpaEntity jpa) {
        if (jpa == null) return null;

        return SubscriptionPaymentRequest.rehydrate(
                jpa.getId(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getTenantId(),
                jpa.getSubscriptionPlanId(),
                jpa.getAmount(),
                jpa.getMpesaPhone(),
                jpa.getPurpose(),
                jpa.getStatus(),
                jpa.getMpesaCheckoutRequestId(),
                jpa.getMpesaReceiptNumber(),
                jpa.getFailureReason()
        );
    }
}
