package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.notification.sms.PhoneMasker;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.observability.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisbursementRetrySweepService {

    private final DisbursementRepository disbursementRepository;
    private final DarajaB2CService darajaB2CService;
    private final PlatformSettingsService platformSettingsService;
    private final TenantRepository tenantRepository;
    private final FinancialAuditService financialAuditService;
    private final BusinessMetrics metrics;

    @Transactional
    public void retryOne(UUID disbursementId) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElse(null);

        if (disbursement == null) {
            log.warn("Disbursement {} not found for retry — skipping", disbursementId);
            return;
        }

        if (disbursement.getStatus() != DisbursementStatus.FAILED) {
            log.info("Disbursement {} is not FAILED (status={}) — skipping retry",
                    disbursementId, disbursement.getStatus());
            return;
        }

        int maxRetries = platformSettingsService.getEffectiveSettings()
                .getDisbursementMaxRetryAttempts();

        if (disbursement.getRetryCount() >= maxRetries) {
            log.warn("Disbursement {} has exhausted retries (retryCount={}, maxRetries={}) — flagging for manual attention",
                    disbursementId, disbursement.getRetryCount(), maxRetries);
            disbursement.markRequiresManualAttention();
            disbursementRepository.save(disbursement);
            return;
        }

        // ── Destination re-validation ────────────────────────────────────
        //
        // A retry replays a row, and the row carries the recipient it was
        // created with. Rows created before the payout destination became
        // server-derived can therefore hold ANY phone number a landlord
        // MANAGER typed into the old endpoint — and a platform owner
        // retrying such a row in good faith would send money there.
        //
        // The row is refused rather than corrected. Rewriting the recipient
        // on a financial record to make a retry succeed is exactly the kind
        // of silent mutation of money history this codebase avoids
        // everywhere else; if the destination is wrong or has legitimately
        // changed, the right answer is a fresh, properly derived
        // disbursement, not an edited old one.
        Tenant landlord = tenantRepository.findById(disbursement.getTenantId()).orElse(null);
        String registeredPayoutPhone = landlord == null ? null : landlord.getPayoutPhoneNumber();

        if (registeredPayoutPhone == null || registeredPayoutPhone.isBlank()) {
            log.warn("Retry refused for disbursement {} — landlord {} has no registered payout number",
                    disbursementId, disbursement.getTenantId());
            financialAuditService.disbursementRetryRefused(
                    disbursement.getTenantId(), disbursementId,
                    "No registered payout number");
            disbursement.markRequiresManualAttention();
            disbursementRepository.save(disbursement);
            return;
        }

        if (!registeredPayoutPhone.equals(disbursement.getRecipientPhone())) {
            log.warn("Retry refused for disbursement {} — stored recipient {} does not match the "
                            + "landlord's registered payout number {}. Raise a new disbursement instead.",
                    disbursementId,
                    PhoneMasker.mask(disbursement.getRecipientPhone()),
                    PhoneMasker.mask(registeredPayoutPhone));
            financialAuditService.disbursementRetryRefused(
                    disbursement.getTenantId(), disbursementId,
                    "Stored recipient does not match the registered payout number");
            metrics.disbursementRefused();
            disbursement.markRequiresManualAttention();
            disbursementRepository.save(disbursement);
            return;
        }

        String originatorConversationId;
        try {
            originatorConversationId = darajaB2CService.initiateB2C(
                    disbursement.getAmount(),
                    disbursement.getRecipientPhone(),
                    disbursement.getRecipientName(),
                    "Retry disbursement " + disbursement.getId(),
                    disbursement.getCommandId()
            );
        } catch (Exception e) {
            disbursement.markFailed("Retry failed: " + e.getMessage(), null);
            disbursementRepository.save(disbursement);
            log.error("Retry failed for disbursement {} — attempt {}/{}",
                    disbursementId, disbursement.getRetryCount(), maxRetries, e);
            return;
        }

        disbursement.markPending(originatorConversationId);
        disbursementRepository.save(disbursement);

        // A retry is a fresh money movement authorised by whoever triggered
        // it, so it gets its own audit row rather than being folded into the
        // original initiation.
        financialAuditService.disbursementRetried(
                disbursement.getTenantId(), disbursementId,
                String.valueOf(disbursement.getAmount()),
                PhoneMasker.mask(disbursement.getRecipientPhone()));
        metrics.disbursementInitiated();

        log.info("Disbursement {} retried successfully. Attempt={} originatorConversationId={}",
                disbursementId, disbursement.getRetryCount(), originatorConversationId);
    }
}
