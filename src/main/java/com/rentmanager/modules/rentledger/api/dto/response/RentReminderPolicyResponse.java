package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;

/**
 * One row of the reminder cadence grid.
 *
 * <p>{@code dayOffset} and {@code label} are included so the settings screen
 * does not have to hard-code the cadence a second time. A milestone added to
 * the enum then appears in the UI without a matching frontend change, rather
 * than silently sending messages nobody can see configured.
 */
public record RentReminderPolicyResponse(
        ReminderMilestone milestone,
        int dayOffset,
        String label,
        boolean enabled,
        boolean smsEnabled,
        boolean emailEnabled,
        boolean whatsappEnabled,
        boolean notifyLandlord
) {

    public static RentReminderPolicyResponse from(RentReminderPolicy policy) {
        return new RentReminderPolicyResponse(
                policy.getMilestone(),
                policy.getMilestone().dayOffset(),
                labelFor(policy.getMilestone()),
                policy.isEnabled(),
                policy.isSmsEnabled(),
                policy.isEmailEnabled(),
                policy.isWhatsappEnabled(),
                policy.isNotifyLandlord()
        );
    }

    private static String labelFor(ReminderMilestone milestone) {
        return switch (milestone) {
            case T_MINUS_7 -> "7 days before due";
            case T_MINUS_3 -> "3 days before due";
            case DUE_TODAY -> "On the due date";
            case OVERDUE_1 -> "1 day overdue";
            case OVERDUE_3 -> "3 days overdue";
            case OVERDUE_7 -> "7 days overdue";
        };
    }
}
