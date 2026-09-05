package com.rentmanager.modules.tenant.application.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily lifecycle sweep for premium subscriptions (Phase 1 dual revenue
 * model, Ratiba autobilling). Modeled on {@code LeaseActionScheduler}
 * conventions: a batch loop that delegates every per-item mutation to the
 * proxied {@link SubscriptionExpirySweepService} so each tenant gets its
 * own transaction and one failure never rolls back the whole sweep.
 *
 * <p>Deliberately performs NO payment initiation - collections arrive via
 * the C2B confirmation callback (Ratiba standing orders) or the Pay-Now
 * STK callback. This scheduler only advances state: period ended unpaid
 * -&gt; GRACE_PERIOD, grace ended unpaid / downgrade period ended -&gt;
 * revert to COMMISSION (LAPSED).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpiryScheduler {

    private final SubscriptionExpirySweepService sweepService;

    /**
     * Runs daily at 1:00 AM - the expiry sweep never initiates payments;
     * collections arrive via the C2B/Ratiba callbacks, this only advances
     * state (period ended -> grace, grace/renewal ended -> COMMISSION).
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "Africa/Nairobi")
    public void sweepSubscriptions() {
        sweepService.enterGraceForExpiredSubscriptions();
        sweepService.revertNonRenewingSubscriptions();
        sweepService.revertOverdueGraceSubscriptions();
        sweepService.expireStalePaymentRequests();
    }

    /**
     * Hourly stale-payment sweep: PENDING subscription STK pushes that got
     * no Daraja callback within the expiry window become EXPIRED so the
     * landlord can retry. Runs far more often than the daily lifecycle
     * sweep so a lost callback never blocks the switch flow for up to 24h.
     * (The switch endpoint also reconciles against Daraja on retry, so a
     * paid-but-unconfirmed request activates premium instead of being
     * charged twice.)
     */
    @Scheduled(cron = "0 5 * * * *", zone = "Africa/Nairobi")
    public void sweepStalePaymentRequests() {
        sweepService.expireStalePaymentRequests();
    }
}
