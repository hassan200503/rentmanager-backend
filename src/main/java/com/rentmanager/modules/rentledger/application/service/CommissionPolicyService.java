package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommissionPolicyService {

    private final CommissionPolicyRepository commissionPolicyRepository;

    /**
     * Returns the active commission rate (as a percentage, e.g. 5.00 = 5%)
     * for the given landlord. First checks for a landlord-specific override;
     * falls back to the platform-wide default. Returns null if no active
     * policy exists (meaning no commission is deducted).
     */
    @Transactional(readOnly = true)
    public BigDecimal getActiveRate(UUID landlordOrgId) {
        if (landlordOrgId != null) {
            CommissionPolicy override = commissionPolicyRepository
                    .findActiveByLandlordOrgId(landlordOrgId).orElse(null);
            if (override != null) {
                log.debug("Found landlord-specific commission policy: landlordOrgId={} rate={}%",
                        landlordOrgId, override.getRatePercent());
                return override.getRatePercent();
            }
        }

        CommissionPolicy defaultPolicy = commissionPolicyRepository.findActiveDefault().orElse(null);
        if (defaultPolicy != null) {
            log.debug("Using platform-default commission policy: rate={}%", defaultPolicy.getRatePercent());
            return defaultPolicy.getRatePercent();
        }

        log.debug("No active commission policy found for landlordOrgId={} — no commission applied", landlordOrgId);
        return null;
    }

    /**
     * Computes the commission amount from a gross amount and rate.
     * Rate is a percentage (e.g. 5.00 = 5%).
     */
    public static BigDecimal computeCommission(BigDecimal grossAmount, BigDecimal ratePercent) {
        if (grossAmount == null || ratePercent == null) return BigDecimal.ZERO;
        return grossAmount.multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Computes the net amount after commission.
     */
    public static BigDecimal computeNetAmount(BigDecimal grossAmount, BigDecimal commission) {
        if (grossAmount == null) return BigDecimal.ZERO;
        BigDecimal net = grossAmount.subtract(commission != null ? commission : BigDecimal.ZERO);
        return net.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : net;
    }

    /**
     * Sets the platform-wide default commission policy.
     * Deactivates any existing active default before saving the new one.
     */
    @Transactional
    public CommissionPolicy setDefaultRate(BigDecimal ratePercent, Instant effectiveFrom, String createdBy) {
        commissionPolicyRepository.findActiveDefault().ifPresent(existing -> {
            existing.deactivate();
            commissionPolicyRepository.save(existing);
        });

        CommissionPolicy policy = CommissionPolicy.create(ratePercent, effectiveFrom, createdBy);
        return commissionPolicyRepository.save(policy);
    }

    /**
     * Sets a landlord-specific override that takes precedence over the default.
     */
    @Transactional
    public CommissionPolicy setLandlordRate(UUID landlordOrgId, BigDecimal ratePercent, Instant effectiveFrom, String createdBy) {
        commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId).ifPresent(existing -> {
            existing.deactivate();
            commissionPolicyRepository.save(existing);
        });

        CommissionPolicy policy = CommissionPolicy.createForLandlord(ratePercent, effectiveFrom, createdBy, landlordOrgId);
        return commissionPolicyRepository.save(policy);
    }
}
