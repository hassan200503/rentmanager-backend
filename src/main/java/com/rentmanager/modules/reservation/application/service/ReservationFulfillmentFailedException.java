package com.rentmanager.modules.reservation.application.service;

/**
 * Thrown by ReservationFulfillmentOrchestrator.on() after compensation has
 * run for a non-optimistic-lock failure, so that Spring's transaction
 * interceptor rolls back on()'s own transaction instead of committing it.
 *
 * Before this class existed, the generic catch (Exception e) branch called
 * compensate() and then returned normally, letting on()'s transaction
 * commit anyway — including any steps that ran before the failure (see
 * project handoff §1, Finding B). Rethrowing (wrapped here, since the
 * original cause may be a checked exception) forces the correct rollback.
 *
 * This is safe to throw uncaught out of on(): it's only ever invoked as an
 * AFTER_COMMIT transactional-event-listener callback, and Spring's
 * TransactionSynchronizationUtils catches and logs Throwable from listener
 * callbacks without affecting the already-committed outer transaction.
 */
public class ReservationFulfillmentFailedException extends RuntimeException {
    public ReservationFulfillmentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}