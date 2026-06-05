package com.rentmanager.shared.exception;

public class BusinessException extends BaseException {

    public BusinessException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}