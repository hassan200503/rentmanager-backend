package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2CDisbursementService {

    private final DisbursementRepository disbursementRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;
    private final DarajaB2CService darajaB2CService;

    /**
     * Initiates a B2C disbursement. Persists the Disbursement record BEFORE
     * calling Daraja, so a crash after the API call but before the result is
     * recorded still leaves a traceable row in INITIATED state.
     */
    @Transactional
    public Disbursement initiateDisbursement(
            UUID tenantId,
            UUID leaseId,
            UUID ledgerEntryId,
            BigDecimal amount,
            String recipientPhone,
            String recipientName,
            String commandId,
            String remarks
    ) {
        Disbursement disbursement = Disbursement.create(
                tenantId, leaseId, ledgerEntryId, amount, recipientPhone, recipientName, commandId
        );
        disbursement = disbursementRepository.save(disbursement);

        String originatorConversationId;
        try {
            originatorConversationId = darajaB2CService.initiateB2C(
                    amount, recipientPhone, recipientName, remarks, commandId
            );
        } catch (Exception e) {
            disbursement.markFailed("Initiation failed: " + e.getMessage(), null);
            disbursementRepository.save(disbursement);
            throw new RentLedgerStateException("B2C initiation failed", ErrorCode.INTERNAL_ERROR);
        }

        disbursement.markPending(originatorConversationId);
        disbursement = disbursementRepository.save(disbursement);

        log.info("B2C disbursement initiated. id={} amount={} recipient={} conversationId={}",
                disbursement.getId(), amount, recipientPhone, originatorConversationId);

        return disbursement;
    }

    /**
     * Handles the B2C ResultURL callback. Updates the disbursement record
     * and posts a REFUND transaction to the rent ledger on success.
     */
    @Transactional
    public Disbursement handleResult(
            UUID disbursementId,
            String resultCode,
            String resultDesc,
            String transactionId,
            String conversationId
    ) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Disbursement not found: " + disbursementId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (disbursement.getStatus() == DisbursementStatus.SUCCESS
                || disbursement.getStatus() == DisbursementStatus.FAILED) {
            log.info("Disbursement {} already has terminal status {} — ignoring duplicate callback",
                    disbursementId, disbursement.getStatus());
            return disbursement;
        }

        if ("0".equals(resultCode)) {
            disbursement.markSuccess(transactionId, conversationId);

            if (disbursement.getLedgerEntryId() != null) {
                rentLedgerApplicationService.applyTransaction(
                        disbursement.getTenantId(),
                        "disbursement-" + disbursement.getId(),
                        disbursement.getLedgerEntryId(),
                        RentTransactionType.REFUND,
                        disbursement.getAmount(),
                        transactionId,
                        RentTransactionSource.MPESA,
                        "SYSTEM",
                        LocalDateTime.now()
                );
            }
        } else {
            disbursement.markFailed(resultDesc, conversationId);
        }

        disbursement = disbursementRepository.save(disbursement);
        log.info("B2C disbursement result processed. id={} resultCode={} status={}",
                disbursementId, resultCode, disbursement.getStatus());
        return disbursement;
    }

    /**
     * Handles the B2C QueueTimeOutURL callback (Safaricom queued but never
     * processed). Marks the disbursement as FAILED with a timeout reason.
     */
    @Transactional
    public Disbursement handleTimeout(UUID disbursementId) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "Disbursement not found: " + disbursementId,
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        if (disbursement.getStatus() != DisbursementStatus.PENDING) {
            log.info("Disbursement {} in state {} — ignoring timeout callback",
                    disbursementId, disbursement.getStatus());
            return disbursement;
        }

        disbursement.markFailed("Queue timeout — Safaricom did not process the request", null);
        disbursement = disbursementRepository.save(disbursement);
        log.warn("B2C disbursement timed out. id={}", disbursementId);
        return disbursement;
    }
}