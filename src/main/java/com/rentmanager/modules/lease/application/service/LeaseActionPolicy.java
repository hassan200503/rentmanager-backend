package com.rentmanager.modules.lease.application.service;

import com.rentmanager.modules.lease.application.dto.request.LeaseActionType;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;

import java.util.List;

/**
 * The lease actions a landlord may take from each status, derived from the
 * guards in Lease and LeaseWorkflowValidator. Published on LeaseDetailResponse
 * so clients render only actions the backend will accept. Still a hint: every
 * action is re-validated when executed (ACTIVATE also requires a held deposit).
 *
 * EXPIRE is deliberately absent; expiry is the nightly scheduler's job.
 * LeaseActionPolicyTest executes every offered action against a real
 * aggregate, so this list cannot silently drift from the domain guards.
 */
public final class LeaseActionPolicy {

    private LeaseActionPolicy() {
    }

    public static List<LeaseActionType> allowedActions(LeaseStatus status) {
        if (status == null) {
            return List.of();
        }
        return switch (status) {
            case DRAFT -> List.of(LeaseActionType.APPROVE, LeaseActionType.REJECT, LeaseActionType.CANCEL);
            case PENDING_APPROVAL -> List.of(LeaseActionType.AWAITING_DEPOSIT, LeaseActionType.REJECT, LeaseActionType.CANCEL);
            case AWAITING_DEPOSIT -> List.of(LeaseActionType.ACTIVATE, LeaseActionType.CANCEL);
            case PENDING_ACTIVATION -> List.of(LeaseActionType.CANCEL);
            case ACTIVE, RENEWED -> List.of(LeaseActionType.RENEW, LeaseActionType.TERMINATE);
            case EXPIRED -> List.of(LeaseActionType.RENEW);
            case CANCELLED, TERMINATED, SUSPENDED -> List.of();
        };
    }
}
