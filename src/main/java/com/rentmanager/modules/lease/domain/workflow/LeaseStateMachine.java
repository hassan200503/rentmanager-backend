package com.rentmanager.modules.lease.domain.workflow;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;

import java.util.EnumMap;
import java.util.Set;

/**
 * SINGLE SOURCE OF TRUTH for Lease transitions
 */
public final class LeaseStateMachine {

    private static final EnumMap<LeaseStatus, Set<LeaseStatus>> TRANSITIONS =
            new EnumMap<>(LeaseStatus.class);

    static {
        TRANSITIONS.put(LeaseStatus.DRAFT, Set.of(
                LeaseStatus.PENDING_APPROVAL,
                LeaseStatus.TERMINATED
        ));

        TRANSITIONS.put(LeaseStatus.PENDING_APPROVAL, Set.of(
                LeaseStatus.ACTIVE,
                LeaseStatus.TERMINATED
        ));

        TRANSITIONS.put(LeaseStatus.ACTIVE, Set.of(
                LeaseStatus.SUSPENDED,
                LeaseStatus.TERMINATED,
                LeaseStatus.EXPIRED
        ));

        TRANSITIONS.put(LeaseStatus.SUSPENDED, Set.of(
                LeaseStatus.ACTIVE,
                LeaseStatus.TERMINATED
        ));

        TRANSITIONS.put(LeaseStatus.EXPIRED, Set.of());
        TRANSITIONS.put(LeaseStatus.TERMINATED, Set.of());
    }

    public static boolean canTransition(LeaseStatus from, LeaseStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }
}