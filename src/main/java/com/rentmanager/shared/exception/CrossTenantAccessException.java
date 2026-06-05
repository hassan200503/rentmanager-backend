package com.rentmanager.shared.exception;

import com.rentmanager.shared.exception.ErrorCode;

public class CrossTenantAccessException extends RuntimeException {

    private final ErrorCode errorCode;

    public CrossTenantAccessException(String message) {
        super(message);
        this.errorCode = ErrorCode.FORBIDDEN;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}