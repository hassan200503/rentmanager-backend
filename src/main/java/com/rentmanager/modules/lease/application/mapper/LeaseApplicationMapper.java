package com.rentmanager.modules.lease.application.mapper;

import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;
import com.rentmanager.modules.lease.application.dto.response.LeaseResponse;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class LeaseApplicationMapper {

    public LeaseResponse toResponse(Lease lease) {

        if (lease == null) return null;

        return new LeaseResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                lease.getPropertyId(),
                lease.getUnitId(),
                lease.getTenantProfileId(),
                LeaseType.valueOf(lease.getLeaseType().name()),
                BillingCycleDTO.valueOf(lease.getBillingCycle().name()),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                lease.getSecurityDeposit(),
                lease.getLateFeeAmount(),
                lease.getGracePeriodDays(),
                lease.isAutoRenew(),
                lease.getStatus().name(),
                toOffset(lease.getCreatedAt()),
                toOffset(lease.getUpdatedAt())
        );
    }

    private LeaseTypeDTO mapLeaseType(Lease lease) {
        return LeaseTypeDTO.valueOf(lease.getLeaseType().name());
    }

    private BillingCycleDTO mapBillingCycle(Lease lease) {
        return BillingCycleDTO.valueOf(lease.getBillingCycle().name());
    }

    private String mapStatus(Lease lease) {
        return lease.getStatus().name();
    }

    private OffsetDateTime toOffset(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}