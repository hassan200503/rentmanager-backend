package com.rentmanager.shared.exception;

public class LeaseStateException extends BusinessException {

    private final ErrorCode errorCode;

    public LeaseStateException(String message, ErrorCode errorCode) {
        super(message, errorCode);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}