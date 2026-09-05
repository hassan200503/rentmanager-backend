package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
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
    private final FinancialAuditService financialAuditService;

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

        log.debug("No active commission policy found for landlordOrgId={} â€” no commission applied", landlordOrgId);
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
        // Captured before deactivation: afterwards the previous rate is only
        // discoverable by reading deactivated rows in the right order.
        BigDecimal previousRate = commissionPolicyRepository.findActiveDefault()
                .map(CommissionPolicy::getRatePercent)
                .orElse(null);

        commissionPolicyRepository.findActiveDefault().ifPresent(existing -> {
            existing.deactivate();
            commissionPolicyRepository.save(existing);
        });

        CommissionPolicy policy = CommissionPolicy.create(ratePercent, effectiveFrom, createdBy);
        CommissionPolicy saved = commissionPolicyRepository.save(policy);

        // The platform-wide default changes what every landlord without an
        // override pays on every future payment, so it is the more
        // consequential of the two rate changes.
        financialAuditService.commissionPolicyChanged(
                null,
                previousRate == null ? null : previousRate.toPlainString(),
                ratePercent == null ? null : ratePercent.toPlainString());

        return saved;
    }

    /**
     * Sets a landlord-specific override that takes precedence over the default.
     */
    @Transactional
    public CommissionPolicy setLandlordRate(UUID landlordOrgId, BigDecimal ratePercent, Instant effectiveFrom, String createdBy) {
        BigDecimal previousRate = commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId)
                .map(CommissionPolicy::getRatePercent)
                .orElse(null);

        commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId).ifPresent(existing -> {
            existing.deactivate();
            commissionPolicyRepository.save(existing);
        });

        CommissionPolicy policy = CommissionPolicy.createForLandlord(ratePercent, effectiveFrom, createdBy, landlordOrgId);
        CommissionPolicy saved = commissionPolicyRepository.save(policy);

        financialAuditService.commissionPolicyChanged(
                landlordOrgId,
                previousRate == null ? null : previousRate.toPlainString(),
                ratePercent == null ? null : ratePercent.toPlainString());

        return saved;
    }

    /**
     * Deactivates the landlord-specific override (if any), so the landlord
     * reverts to the platform-wide default rate. No-op when there is no active
     * override; follows the same deactivate-don't-mutate rule as the setters.
     */
    @Transactional
    public void clearLandlordRate(UUID landlordOrgId) {
        commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId).ifPresent(existing -> {
            BigDecimal previousRate = existing.getRatePercent();

            existing.deactivate();
            commissionPolicyRepository.save(existing);

            // Recorded as a change to "cleared" rather than skipped. Removing
            // an override moves the landlord back to the platform default,
            // which may be higher or lower — it is a rate change either way.
            financialAuditService.commissionPolicyChanged(
                    landlordOrgId,
                    previousRate == null ? null : previousRate.toPlainString(),
                    null);
        });
    }
}
