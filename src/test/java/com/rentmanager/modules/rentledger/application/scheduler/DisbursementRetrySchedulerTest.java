package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisbursementRetrySchedulerTest {

    @Mock
    private DisbursementRepository disbursementRepository;
    @Mock
    private DisbursementRetrySweepService sweepService;

    private DisbursementRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DisbursementRetryScheduler(disbursementRepository, sweepService);
    }

    private Disbursement failedDisbursement() {
        return Disbursement.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5000.00"), "+254712345678", "Test Landlord",
                "BusinessPayment", DisbursementStatus.FAILED,
                null, "CONV", "OCID",
                "Timeout", 1, false,
                Instant.now(), Instant.now(), 0L
        );
    }

    private Disbursement pendingDisbursement() {
        return Disbursement.rehydrate(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("5000.00"), "+254712345678", "Test Landlord",
                "BusinessPayment", DisbursementStatus.PENDING,
                null, "CONV", "OCID",
                null, 0, false,
                Instant.now().minusSeconds(7200), Instant.now(), 0L
        );
    }

    @Nested
    class RetryFailed {

        @Test
        void retriesAllFailedDisbursementsUnderCap() {
            List<Disbursement> failed = List.of(failedDisbursement(), failedDisbursement());
            when(disbursementRepository.findByStatusInAndRetryCountLessThan(
                    eq(List.of(DisbursementStatus.FAILED)), eq(3)
            )).thenReturn(failed);

            scheduler.retryFailedDisbursements();

            verify(sweepService, times(2)).retryOne(any(UUID.class));
        }

        @Test
        void doesNothingWhenNoFailed() {
            when(disbursementRepository.findByStatusInAndRetryCountLessThan(
                    anyList(), anyInt()
            )).thenReturn(List.of());

            scheduler.retryFailedDisbursements();

            verify(sweepService, never()).retryOne(any());
        }

        @Test
        void continuesAfterIndividualFailure() {
            UUID goodId = UUID.randomUUID();
            UUID badId = UUID.randomUUID();

            Disbursement good = Disbursement.rehydrate(
                    goodId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new BigDecimal("5000.00"), "+254712345678", "Test Landlord",
                    "BusinessPayment", DisbursementStatus.FAILED,
                    null, "CONV", "OCID",
                    "Timeout", 1, false,
                    Instant.now(), Instant.now(), 0L
            );
            Disbursement bad = Disbursement.rehydrate(
                    badId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    new BigDecimal("3000.00"), "+254712345678", "Test Landlord",
                    "BusinessPayment", DisbursementStatus.FAILED,
                    null, "CONV", "OCID",
                    "Timeout", 1, false,
                    Instant.now(), Instant.now(), 0L
            );

            when(disbursementRepository.findByStatusInAndRetryCountLessThan(
                    eq(List.of(DisbursementStatus.FAILED)), eq(3)
            )).thenReturn(List.of(good, bad));

            doThrow(new RuntimeException("Sweep failed")).when(sweepService).retryOne(badId);
            doNothing().when(sweepService).retryOne(goodId);

            scheduler.retryFailedDisbursements();

            verify(sweepService).retryOne(goodId);
            verify(sweepService).retryOne(badId);
        }
    }

    @Nested
    class RetryStuckPending {

        @Test
        void retriesStuckPendingOlderThanCutoff() {
            List<Disbursement> stuck = List.of(pendingDisbursement());
            when(disbursementRepository.findByStatusInAndCreatedAtBefore(
                    eq(List.of(DisbursementStatus.PENDING)), any(Instant.class)
            )).thenReturn(stuck);

            scheduler.retryStuckPendingDisbursements();

            verify(sweepService).retryOne(any(UUID.class));
        }

        @Test
        void doesNothingWhenNoStuckPending() {
            when(disbursementRepository.findByStatusInAndCreatedAtBefore(
                    anyList(), any(Instant.class)
            )).thenReturn(List.of());

            scheduler.retryStuckPendingDisbursements();

            verify(sweepService, never()).retryOne(any());
        }
    }
}
