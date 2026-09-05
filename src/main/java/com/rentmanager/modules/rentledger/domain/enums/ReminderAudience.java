package com.rentmanager.modules.rentledger.domain.enums;

/**
 * Who a reminder was addressed to.
 *
 * <p>Part of the {@code rent_reminders} dedupe key, because one milestone
 * legitimately produces two messages: at {@link ReminderMilestone#OVERDUE_7}
 * the renter is told they are a week late and the landlord is told the same
 * thing about their tenant. Those are different messages to different people
 * and must not deduplicate against each other.
 */
public enum ReminderAudience {

    /** The person who owes the rent. */
    RENTER,

    /** The landlord organisation that is owed it. */
    LANDLORD
}
