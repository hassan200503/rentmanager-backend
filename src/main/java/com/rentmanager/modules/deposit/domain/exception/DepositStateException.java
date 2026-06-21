package com.rentmanager.modules.deposit.domain.exception;

import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;

public class DepositStateException extends BusinessException {

    private final ErrorCode errorCode;

    public DepositStateException(String message, ErrorCode errorCode) {
        super(message, errorCode);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}