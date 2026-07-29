package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CommissionPolicyResponse(
        UUID id,
        UUID landlordOrgId,
        BigDecimal ratePercent,
        Instant effectiveFrom,
        boolean active,
        String createdBy,
        Instant createdAt
) {
    public static CommissionPolicyResponse from(CommissionPolicy policy) {
        return new CommissionPolicyResponse(
                policy.getId(),
                policy.getLandlordOrgId(),
                policy.getRatePercent(),
                policy.getEffectiveFrom(),
                policy.isActive(),
                policy.getCreatedBy(),
                policy.getCreatedAt()
        );
    }
}
