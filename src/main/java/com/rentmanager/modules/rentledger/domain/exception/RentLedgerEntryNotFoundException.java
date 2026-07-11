package com.rentmanager.modules.rentledger.domain.exception;

import java.util.UUID;

/**
 * Mirrors PropertyNotFoundException's shape exactly: plain RuntimeException,
 * NOT BusinessException, so it does not fall into GlobalExceptionHandler's
 * BusinessException -> 400 handler. Mapped to its own 404 handler instead.
 * Distinct from RentLedgerStateException, which remains for actual invariant
 * violations (illegal transitions, invalid amounts) — this is exclusively
 * for "no entry exists with this id for this tenant."
 */
public class RentLedgerEntryNotFoundException extends RuntimeException {

    private final UUID entryId;

    public RentLedgerEntryNotFoundException(UUID entryId, String message) {
        super(message);
        this.entryId = entryId;
    }

    public UUID getEntryId() {
        return entryId;
    }
}