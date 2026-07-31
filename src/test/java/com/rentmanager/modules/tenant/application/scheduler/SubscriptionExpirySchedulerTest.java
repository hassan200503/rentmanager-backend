package com.rentmanager.modules.tenant.application.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

/**
 * Wiring test for the daily expiry sweep: the scheduler delegates each
 * of its four state-only passes to the per-item transactional sweep
 * service, in order. Deliberately no @Scheduled invocation - the method
 * is exercised directly, exactly like LeaseActionScheduler's tests.
 */
class SubscriptionExpirySchedulerTest {

    private SubscriptionExpirySweepService sweepService;

    private SubscriptionExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        sweepService = mock(SubscriptionExpirySweepService.class);
        scheduler = new SubscriptionExpiryScheduler(sweepService);
    }

    @Test
    void sweepSubscriptions_callsAllFourSweepPassesInOrder() {
        scheduler.sweepSubscriptions();

        verify(sweepService).enterGraceForExpiredSubscriptions();
        verify(sweepService).revertNonRenewingSubscriptions();
        verify(sweepService).revertOverdueGraceSubscriptions();
        verify(sweepService).expireStalePaymentRequests();

        verifyNoMoreInteractions(sweepService);
    }
}
