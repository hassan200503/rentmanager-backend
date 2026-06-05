package com.rentmanager.modules.audit.domain.enums;

public enum AuditAction {

    // Auth
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // User
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,

    // Unit
    UNIT_CREATED,
    UNIT_UPDATED,
    UNIT_DELETED,

    // Lease
    LEASE_CREATED,
    LEASE_UPDATED,
    LEASE_TERMINATED,

    // System
    SYSTEM_EVENT
}