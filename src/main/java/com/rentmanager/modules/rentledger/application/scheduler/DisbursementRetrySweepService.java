package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
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

        log.info("Disbursement {} retried successfully. Attempt={} originatorConversationId={}",
                disbursementId, disbursement.getRetryCount(), originatorConversationId);
    }
}
