package com.rentmanager.contract.common;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        long timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(
                true,
                "success",
                data,
                null,
                System.currentTimeMillis()
        );
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(
                true,
                message,
                data,
                null,
                System.currentTimeMillis()
        );
    }

    public static <T> ApiResponse<T> fail(String message, String errorCode) {
        return new ApiResponse<>(
                false,
                message,
                null,
                errorCode,
                System.currentTimeMillis()
        );
    }
}