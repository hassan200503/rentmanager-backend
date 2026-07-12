package com.rentmanager.modules.lease.application.dto.request;

public enum LeaseStatusDTO {
    DRAFT,
    PENDING_APPROVAL,
    AWAITING_DEPOSIT,
    PENDING_ACTIVATION,
    ACTIVE,
    RENEWED,
    EXPIRED,
    CANCELLED,
    TERMINATED,
    SUSPENDED
}