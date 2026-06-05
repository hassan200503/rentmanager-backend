package com.rentmanager.shared.exception;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message, ErrorCode errorCode) {
        super(message, errorCode);
    }
}