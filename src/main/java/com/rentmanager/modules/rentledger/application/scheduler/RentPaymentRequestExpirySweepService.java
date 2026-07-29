package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentRequestExpirySweepService {

    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final EntityManager entityManager;

    @Transactional
    public void expireOne(UUID requestId) {
        RentPaymentRequest request = rentPaymentRequestRepository.findById(requestId)
                .orElse(null);

        if (request == null) {
            log.debug("RentPaymentRequest already deleted or not found. requestId={}", requestId);
            return;
        }

        if (request.getStatus() != RentPaymentRequestStatus.PENDING) {
            log.info("RentPaymentRequest no longer PENDING, skipping. requestId={} currentStatus={}",
                    requestId, request.getStatus());
            return;
        }

        request.markFailed();
        rentPaymentRequestRepository.save(request);
        entityManager.flush();

        log.warn("Expired stale RentPaymentRequest (no M-Pesa callback received). " +
                "requestId={} amount={} createdAt={}",
                requestId, request.getAmount(), request.getCreatedAt());
    }
}