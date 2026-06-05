package com.rentmanager.modules.lease.domain.service;

import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class LeaseDomainService {

    private final LeaseRepository leaseRepository;

    public LeaseDomainService(LeaseRepository leaseRepository) {
        this.leaseRepository = leaseRepository;
    }

    /**
     * Cross-aggregate rule:
     * ensures a unit cannot have more than one active lease per tenant
     */
    public void validateUnitNotOccupied(UUID tenantId, UUID unitId) {

        boolean hasActiveLease =
                leaseRepository.existsActiveLeaseByUnitIdAndTenantId(unitId, tenantId);

        if (hasActiveLease) {
            throw new IllegalStateException("Unit already has an active lease");
        }
    }

    // ===================== EXISTING DOMAIN METHOD (UNCHANGED) =====================
    public Lease create(Lease lease) {
        return lease;
    }

    // ===================== NEW OVERLOAD (FIX FOR YOUR ERROR) =====================
    public Lease create(
            UUID tenantId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            String leaseNumber,
            LeaseType leaseType,
            BillingCycle billingCycle,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRent,
            BigDecimal securityDeposit,
            BigDecimal lateFeeAmount,
            Integer gracePeriodDays,
            boolean autoRenew
    ) {
        return Lease.create(
                tenantId,
                propertyId,
                unitId,
                tenantProfileId,
                leaseNumber,
                leaseType,
                billingCycle,
                startDate,
                endDate,
                monthlyRent,
                securityDeposit,
                lateFeeAmount,
                gracePeriodDays,
                autoRenew
        );
    }

    public void preActivateChecks(Lease lease) {
        // domain rule placeholder (keeps compile + extension point)
    }

    public void activate(Lease lease) {
        lease.activate();
    }

    public void approve(Lease lease) {
        lease.approve();
    }

    public void reject(Lease lease, String reason) {
        lease.reject(reason);
    }

    public void terminate(Lease lease, TerminationType type, String reason) {
        lease.terminate(type, reason, "SYSTEM", lease.getTenantId());
    }

    public void renew(Lease lease, LocalDate start, LocalDate end) {
        lease.renew(start, end,lease.getTenantId(), "SYSTEM");
    }
}