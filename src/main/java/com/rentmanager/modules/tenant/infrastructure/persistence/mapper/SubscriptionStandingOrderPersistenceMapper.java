package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionStandingOrderJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionStandingOrderPersistenceMapper {

    public SubscriptionStandingOrderJpaEntity toJpaEntity(SubscriptionStandingOrder order) {
        SubscriptionStandingOrderJpaEntity jpa = new SubscriptionStandingOrderJpaEntity();
        jpa.setId(order.getId());
        jpa.setVersion(order.getVersion());
        jpa.setTenantId(order.getTenantId());
        jpa.setAccountReference(order.getAccountReference());
        jpa.setAmount(order.getAmount());
        jpa.setFrequency(order.getFrequency());
        jpa.setStatus(order.getStatus());
        jpa.setRatibaResponseRefId(order.getRatibaResponseRefId());
        jpa.setRatibaTransactionId(order.getRatibaTransactionId());
        jpa.setStartDate(order.getStartDate());
        jpa.setEndDate(order.getEndDate());
        jpa.setFailureReason(order.getFailureReason());
        return jpa;
    }

    public SubscriptionStandingOrder toDomain(SubscriptionStandingOrderJpaEntity jpa) {
        return SubscriptionStandingOrder.rehydrate(
                jpa.getId(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getTenantId(),
                jpa.getAccountReference(),
                jpa.getAmount(),
                jpa.getFrequency(),
                jpa.getStatus(),
                jpa.getRatibaResponseRefId(),
                jpa.getRatibaTransactionId(),
                jpa.getStartDate(),
                jpa.getEndDate(),
                jpa.getFailureReason()
        );
    }
}
