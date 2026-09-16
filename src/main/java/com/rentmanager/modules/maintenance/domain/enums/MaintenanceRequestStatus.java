package com.rentmanager.modules.maintenance.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a maintenance request.
 *
 * <p>Transitions (TD-132). Every status change texts or pushes the renter, so
 * moves that tell them something false are refused:
 * <ul>
 *   <li>SUBMITTED means "reported, nobody has acted yet" — nothing moves back
 *       to it.</li>
 *   <li>CANCELLED is terminal.</li>
 *   <li>COMPLETED can only be reopened, to IN_PROGRESS.</li>
 *   <li>The working states (IN_REVIEW, SCHEDULED, IN_PROGRESS) move freely
 *       among themselves — rescheduling a visit already in progress is real —
 *       and can be closed as COMPLETED or CANCELLED.</li>
 * </ul>
 * Staying on the same status is always allowed: that is how a landlord
 * replies with a note alone.
 */
public enum MaintenanceRequestStatus {
    SUBMITTED,
    IN_REVIEW,
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public Set<MaintenanceRequestStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(IN_REVIEW, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED);
            case IN_REVIEW -> EnumSet.of(SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED);
            case SCHEDULED -> EnumSet.of(IN_REVIEW, IN_PROGRESS, COMPLETED, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(IN_REVIEW, SCHEDULED, COMPLETED, CANCELLED);
            case COMPLETED -> EnumSet.of(IN_PROGRESS);
            case CANCELLED -> EnumSet.noneOf(MaintenanceRequestStatus.class);
        };
    }

    public boolean canMoveTo(MaintenanceRequestStatus target) {
        return target == this || allowedNext().contains(target);
    }
}
