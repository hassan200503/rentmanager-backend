package com.rentmanager.modules.rentledger.domain.exception;

import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;

/**
 * VERIFIED against LeaseStateException.java and DepositStateException.java —
 * both extend BusinessException with an identical (message, errorCode)
 * constructor that delegates via super(message, errorCode). This class
 * matches that shape exactly rather than approximating it.
 *
 * Thrown for any invariant violation on RentTransaction or RentLedgerEntry:
 * invalid construction arguments, illegal state transitions (e.g. applying
 * a payment to a PAID entry, calling markOverdue() on a settled entry,
 * resolving an overpayment that doesn't exist), or a caller passing the
 * wrong RentTransactionType into a method that only accepts one kind.
 */
public class RentLedgerStateException extends BusinessException {

    private final ErrorCode errorCode;

    public RentLedgerStateException(String message, ErrorCode errorCode) {
        super(message, errorCode);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}