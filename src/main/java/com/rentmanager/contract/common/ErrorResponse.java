package com.rentmanager.contract.common;

public record ErrorResponse(
        String errorCode,
        String message,
        String path,
        long timestamp
) {}