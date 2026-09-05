package com.rentmanager.modules.rentledger.domain.enums;

import java.time.LocalDate;
import java.util.Optional;

/**
 * A point in the rent reminder cadence, expressed as a signed offset in days
 * from a {@code RentLedgerEntry}'s due date.
 *
 * <pre>
 *   T_MINUS_7   T_MINUS_3    DUE_TODAY   OVERDUE_1  OVERDUE_3   OVERDUE_7
 *      -7          -3            0          +1         +3          +7
 *  ─────┼───────────┼─────────────┼──────────┼──────────┼───────────┼────►
 *                            due date
 * </pre>
 *
 * <h2>Why the offset lives here</h2>
 * The scheduler asks a single question each morning: "for a run on date D,
 * which due date would place an entry at this milestone?" The answer is
 * {@code D - offset}, and it belongs with the milestone rather than being
 * re-derived at each call site. {@link #dueDateFor(LocalDate)} is that
 * arithmetic, and it is the only place it exists.
 *
 * <h2>What a milestone is not</h2>
 * A milestone is <strong>not</strong> a ledger state. {@link RentLedgerStatus}
 * owns what a renter owes and whether it is overdue; this enum owns only when
 * we speak to them about it. The distinction matters: escalation is a
 * communications decision, and if it were modelled as ledger status then a
 * failed SMS could corrupt financial state. Reminders read the ledger and
 * never write to it.
 *
 * <p>In particular {@code OVERDUE_7} means "seven days past due and we are
 * telling the landlord", not a new financial condition. The entry's status is
 * still {@code OVERDUE}, exactly as it was on day one.
 */
public enum ReminderMilestone {

    /** A week out. Off by default — nobody has forgotten yet. */
    T_MINUS_7(-7),

    /** The useful nudge, far enough ahead to arrange money. */
    T_MINUS_3(-3),

    /** Due today. The message most likely to produce a payment. */
    DUE_TODAY(0),

    /** One day late — usually timing rather than refusal. */
    OVERDUE_1(1),

    /** Three days late. Now it is a pattern. */
    OVERDUE_3(3),

    /** A week late. Escalation point: the landlord is told. */
    OVERDUE_7(7);

    private final int dayOffset;

    ReminderMilestone(int dayOffset) {
        this.dayOffset = dayOffset;
    }

    /** Signed days from the due date. Negative is before, positive is after. */
    public int dayOffset() {
        return dayOffset;
    }

    /**
     * The due date an entry must carry to sit at this milestone on
     * {@code runDate}. A run on 2026-09-01 looks for due date 2026-09-08 to
     * find its {@code T_MINUS_7} candidates, and 2026-08-25 for
     * {@code OVERDUE_7}.
     */
    public LocalDate dueDateFor(LocalDate runDate) {
        return runDate.minusDays(dayOffset);
    }

    /** True for milestones that fire before money is owed. */
    public boolean isPreDue() {
        return dayOffset < 0;
    }

    /** True for milestones that fire after the due date has passed. */
    public boolean isPostDue() {
        return dayOffset > 0;
    }

    /**
     * The widest span of due dates any milestone can be interested in on a
     * single run, used to fetch every candidate in one query instead of one
     * query per milestone.
     */
    public static LocalDate earliestDueDateOfInterest(LocalDate runDate) {
        return runDate.minusDays(maxOffset());
    }

    /** @see #earliestDueDateOfInterest(LocalDate) */
    public static LocalDate latestDueDateOfInterest(LocalDate runDate) {
        return runDate.minusDays(minOffset());
    }

    private static int maxOffset() {
        int max = Integer.MIN_VALUE;
        for (ReminderMilestone m : values()) {
            max = Math.max(max, m.dayOffset);
        }
        return max;
    }

    private static int minOffset() {
        int min = Integer.MAX_VALUE;
        for (ReminderMilestone m : values()) {
            min = Math.min(min, m.dayOffset);
        }
        return min;
    }

    /**
     * The milestone an entry due on {@code dueDate} sits at when the sweep
     * runs on {@code runDate}, or empty when the gap matches no milestone.
     *
     * <p>Most days fall between milestones — an entry four days overdue is at
     * no milestone at all and must not be messaged. Returning empty rather
     * than a nearest match is deliberate: "close enough" is how a renter ends
     * up hearing from you every single day.
     */
    public static Optional<ReminderMilestone> at(LocalDate runDate, LocalDate dueDate) {
        long gap = java.time.temporal.ChronoUnit.DAYS.between(dueDate, runDate);
        if (gap < Integer.MIN_VALUE || gap > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        for (ReminderMilestone m : values()) {
            if (m.dayOffset == gap) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
