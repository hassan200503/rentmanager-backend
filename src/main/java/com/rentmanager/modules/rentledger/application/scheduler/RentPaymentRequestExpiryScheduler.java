package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentRequestExpiryScheduler {

    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final RentPaymentRequestExpirySweepService sweepService;

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void expireStaleRentPaymentRequests() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);

        List<RentPaymentRequest> stale = rentPaymentRequestRepository
                .findByStatusAndCreatedAtBefore(RentPaymentRequestStatus.PENDING, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.info("Stale RentPaymentRequest sweep found {} candidate(s) older than {}", stale.size(), cutoff);

        for (RentPaymentRequest request : stale) {
            try {
                sweepService.expireOne(request.getId());
            } catch (Exception e) {
                log.error("Failed to expire stale RentPaymentRequest — will retry on next sweep. " +
                        "requestId={}", request.getId(), e);
            }
        }
    }
}