package com.rentmanager.modules.rentledger.domain.exception;

/**
 * Thrown when a renter requests a second M-Pesa STK push before the cooldown
 * from their previous request has elapsed (StkPushRateLimiter). Plain
 * RuntimeException, not BusinessException, so it is not swallowed into the
 * generic 400 BUSINESS handler — it gets its own 429 handler with a
 * Retry-After header, mirroring RentLedgerEntryNotFoundException's approach
 * of a dedicated exception + dedicated handler for a status GlobalExceptionHandler
 * doesn't otherwise produce.
 */
public class StkPushRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public StkPushRateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
