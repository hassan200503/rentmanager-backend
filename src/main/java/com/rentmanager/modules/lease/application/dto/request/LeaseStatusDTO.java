package com.rentmanager.modules.lease.application.dto.request;

public enum LeaseStatusDTO {
    DRAFT,
    PENDING_APPROVAL,
    AWAITING_DEPOSIT,
    ACTIVE,
    TERMINATED,
    REJECTED,
    EXPIRED,
    RENEWED
}