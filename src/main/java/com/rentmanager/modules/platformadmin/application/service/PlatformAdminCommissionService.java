package com.rentmanager.modules.platformadmin.application.service;

import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Super-admin use of the commission policy engine. An {@code OVERRIDE} is a
 * landlord-specific active policy; finding none means the landlord is on the
 * platform {@code DEFAULT} (or no commission at all if no policy exists).
 * Setting/clearing an override only touches the landlord's own policy rows —
 * the platform default stays untouched.
 */
@Slf4j
@Service
public class PlatformAdminCommissionService {

    public static final BigDecimal MIN_RATE = BigDecimal.ZERO;
    public static final BigDecimal MAX_RATE = new BigDecimal("100.00");
    public static final String SOURCE_OVERRIDE = "OVERRIDE";
    public static final String SOURCE_DEFAULT = "DEFAULT";

    private final CommissionPolicyService commissionPolicyService;
    private final CommissionPolicyRepository commissionPolicyRepository;

    public PlatformAdminCommissionService(
            CommissionPolicyService commissionPolicyService,
            CommissionPolicyRepository commissionPolicyRepository) {
        this.commissionPolicyService = commissionPolicyService;
        this.commissionPolicyRepository = commissionPolicyRepository;
    }

    @Transactional(readOnly = true)
    public LandlordCommissionResponse getCommissionFor(UUID landlordOrgId) {
        Optional<CommissionPolicy> override = commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId);
        if (override.isPresent()) {
            CommissionPolicy policy = override.get();
            return new LandlordCommissionResponse(
                    landlordOrgId, policy.getRatePercent(), SOURCE_OVERRIDE,
                    policy.getEffectiveFrom(), policy.getUpdatedAt());
        }
        BigDecimal defaultRate = commissionPolicyService.getActiveRate(landlordOrgId);
        return new LandlordCommissionResponse(landlordOrgId, defaultRate, SOURCE_DEFAULT, null, null);
    }

    @Transactional
    public LandlordCommissionResponse setCommission(UUID landlordOrgId, BigDecimal ratePercent, String createdBy) {
        validateRate(ratePercent);
        CommissionPolicy policy = commissionPolicyService.setLandlordRate(
                landlordOrgId, ratePercent, Instant.now(), createdBy);
        log.info("Platform admin set commission override: landlordOrgId={} rate={}% actor={}",
                landlordOrgId, ratePercent, createdBy);
        return new LandlordCommissionResponse(
                landlordOrgId, policy.getRatePercent(), SOURCE_OVERRIDE,
                policy.getEffectiveFrom(), policy.getUpdatedAt());
    }

    @Transactional
    public void clearCommission(UUID landlordOrgId) {
        commissionPolicyService.clearLandlordRate(landlordOrgId);
        log.info("Platform admin cleared commission override: landlordOrgId={}", landlordOrgId);
    }


    @Transactional(readOnly = true)
    public LandlordCommissionResponse getPlatformDefault() {
        CommissionPolicy def = commissionPolicyRepository.findActiveDefault().orElse(null);
        if (def != null) {
            return new LandlordCommissionResponse(
                    null, def.getRatePercent(), SOURCE_DEFAULT, def.getEffectiveFrom(), def.getUpdatedAt());
        }
        // No active policy means no commission is deducted - the true effective state.
        return new LandlordCommissionResponse(null, null, SOURCE_DEFAULT, null, null);
    }

    @Transactional
    public LandlordCommissionResponse setPlatformDefault(BigDecimal ratePercent, String createdBy) {
        validateRate(ratePercent);
        CommissionPolicy policy = commissionPolicyService.setDefaultRate(ratePercent, Instant.now(), createdBy);
        log.info("Platform admin set platform default commission: rate={}% actor={}", ratePercent, createdBy);
        return new LandlordCommissionResponse(
                null, policy.getRatePercent(), SOURCE_DEFAULT, policy.getEffectiveFrom(), policy.getUpdatedAt());
    }
    private void validateRate(BigDecimal ratePercent) {
        if (ratePercent == null) {
            throw new BusinessException("Commission rate is required", ErrorCode.VALIDATION_ERROR);
        }
        if (ratePercent.compareTo(MIN_RATE) < 0 || ratePercent.compareTo(MAX_RATE) > 0) {
            throw new BusinessException("Commission rate must be between 0 and 100", ErrorCode.VALIDATION_ERROR);
        }
    }
}