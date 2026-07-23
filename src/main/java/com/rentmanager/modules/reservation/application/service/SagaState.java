package com.rentmanager.modules.reservation.application.service;

import java.util.UUID;

/**
 * Mutable tracker for what a saga run actually accomplished, used
 * exclusively to scope compensation correctly if a later step fails.
 */
class SagaState {
    String clerkUserId;
    boolean clerkUserCreatedThisRun;

    UUID tenantProfileId;
    boolean tenantProfileCreatedThisRun;

    UUID leaseId;
    boolean leaseCreated;

    boolean depositPosted;

    UUID unitId;
    boolean unitReserved;
}