package com.rentmanager.shared.exception;

public class LeaseValidationException extends RuntimeException {

    private final ErrorCode errorCode;

    public LeaseValidationException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}