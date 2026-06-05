package com.rentmanager.shared.exception;

public record FieldError(
        String field,
        String message,
        ErrorCode errorCode
) {}