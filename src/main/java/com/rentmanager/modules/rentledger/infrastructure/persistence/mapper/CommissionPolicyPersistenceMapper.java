package com.rentmanager.modules.rentledger.infrastructure.persistence.mapper;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.CommissionPolicyJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class CommissionPolicyPersistenceMapper {

    public CommissionPolicyJpaEntity toJpaEntity(CommissionPolicy policy) {
        if (policy == null) return null;

        CommissionPolicyJpaEntity jpa = new CommissionPolicyJpaEntity();
        jpa.setId(policy.getId());
        jpa.setVersion(policy.getVersion());
        jpa.setLandlordOrgId(policy.getLandlordOrgId());
        jpa.setRatePercent(policy.getRatePercent());
        jpa.setEffectiveFrom(policy.getEffectiveFrom());
        jpa.setActive(policy.isActive());
        jpa.setCreatedBy(policy.getCreatedBy());
        return jpa;
    }

    public CommissionPolicy toDomain(CommissionPolicyJpaEntity jpa) {
        if (jpa == null) return null;

        return CommissionPolicy.rehydrate(
                jpa.getId(),
                jpa.getVersion(),
                jpa.getLandlordOrgId(),
                jpa.getRatePercent(),
                jpa.getEffectiveFrom(),
                jpa.isActive(),
                jpa.getCreatedBy(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}
