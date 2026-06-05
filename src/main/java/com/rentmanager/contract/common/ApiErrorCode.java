package com.rentmanager.contract.common;

public enum ApiErrorCode {

    // GENERAL
    VALIDATION_ERROR,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,

    // DOMAIN
    LEASE_NOT_FOUND,
    LEASE_INVALID_STATE,
    LEASE_ALREADY_ACTIVE,
    LEASE_ALREADY_TERMINATED,

    // SYSTEM
    INTERNAL_ERROR,
    DATABASE_ERROR
}