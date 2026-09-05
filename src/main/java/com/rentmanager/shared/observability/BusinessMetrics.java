package com.rentmanager.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * The handful of numbers that say whether this system is actually working.
 *
 * <h2>Business metrics, not JVM metrics</h2>
 * Actuator already reports heap, threads and connection pool usage for free,
 * and none of that answers the question an operator actually has. A JVM can be
 * perfectly healthy while no rent reminder has gone out for three days because
 * an SMS provider is rejecting the credentials — which is precisely the
 * failure this codebase was exposed to, since {@code NotificationDelivery}
 * gives up after {@code MAX_ATTEMPTS} and tells nobody.
 *
 * <p>Each counter here exists because its absence would hide a specific,
 * plausible failure:
 *
 * <ul>
 *   <li><b>reminders</b> — a sweep that silently sends nothing looks identical
 *       to a quiet month. The alert is on the rate falling to zero, not on an
 *       error appearing.</li>
 *   <li><b>notification delivery</b> — the retry budget is three attempts and
 *       then silence. A rising failure count is the only signal before
 *       landlords start asking why tenants were never told.</li>
 *   <li><b>disbursements</b> — refusals are counted separately from failures
 *       on purpose. A run of refusals against one account is what an attempt
 *       to drain funds looks like; a run of failures is Safaricom being down.
 *       Conflating them means the alert for one is drowned by the other.</li>
 *   <li><b>payments</b> — the rate this business runs on. A drop is either an
 *       outage or a callback misconfiguration, and both need to be noticed in
 *       minutes rather than at month end.</li>
 * </ul>
 *
 * <h2>Never throws</h2>
 * A metric that fails must not break the operation it was measuring.
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    private final Counter remindersSent;
    private final Counter remindersAlreadySent;
    private final Counter remindersFailed;
    private final Counter paymentsApplied;
    private final Counter disbursementsInitiated;
    private final Counter disbursementsRefused;
    private final Counter disbursementsFailed;
    private final Counter notificationsDelivered;
    private final Counter notificationsExhausted;
    private final Counter unmatchedPaymentsResolved;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.remindersSent = counter("rentmanager.reminders.sent",
                "Rent reminders queued for delivery");
        this.remindersAlreadySent = counter("rentmanager.reminders.already_sent",
                "Reminders skipped because the idempotency guard had already recorded them");
        this.remindersFailed = counter("rentmanager.reminders.failed",
                "Ledger entries the reminder sweep could not process");

        this.paymentsApplied = counter("rentmanager.payments.applied",
                "Rent payments successfully applied to the ledger");

        this.disbursementsInitiated = counter("rentmanager.disbursements.initiated",
                "Payouts raised to landlords");
        this.disbursementsRefused = counter("rentmanager.disbursements.refused",
                "Payouts rejected by an authorisation guard before any money moved");
        this.disbursementsFailed = counter("rentmanager.disbursements.failed",
                "Payouts that failed at the provider");

        this.notificationsDelivered = counter("rentmanager.notifications.delivered",
                "Outbox deliveries accepted by a channel");
        this.notificationsExhausted = counter("rentmanager.notifications.exhausted",
                "Outbox deliveries abandoned after exhausting their retries");

        this.unmatchedPaymentsResolved = counter("rentmanager.unmatched_payments.resolved",
                "Orphan receipts a human pointed at a lease");
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    public void reminderSent() { safeIncrement(remindersSent); }

    public void reminderAlreadySent() { safeIncrement(remindersAlreadySent); }

    public void reminderFailed() { safeIncrement(remindersFailed); }

    public void reminderSweepCompleted(int sent, int alreadySent, int failed) {
        safeIncrement(remindersSent, sent);
        safeIncrement(remindersAlreadySent, alreadySent);
        safeIncrement(remindersFailed, failed);
    }

    public void paymentApplied() { safeIncrement(paymentsApplied); }

    public void disbursementInitiated() { safeIncrement(disbursementsInitiated); }

    public void disbursementRefused() { safeIncrement(disbursementsRefused); }

    public void disbursementFailed() { safeIncrement(disbursementsFailed); }

    public void notificationDelivered() { safeIncrement(notificationsDelivered); }

    public void notificationExhausted() { safeIncrement(notificationsExhausted); }

    public void unmatchedPaymentResolved() { safeIncrement(unmatchedPaymentsResolved); }

    private static void safeIncrement(Counter counter) {
        safeIncrement(counter, 1);
    }

    private static void safeIncrement(Counter counter, double amount) {
        if (amount <= 0) {
            return;
        }
        try {
            counter.increment(amount);
        } catch (Exception ignored) {
            // A metric must never be the reason a payout or a reminder fails.
        }
    }
}
