package com.rentmanager.modules.rentledger.domain.enums;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderMilestoneTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 9, 1);

    @Test
    void dueDateForResolvesEachMilestoneRelativeToTheRunDate() {
        assertThat(ReminderMilestone.T_MINUS_7.dueDateFor(RUN_DATE)).isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(ReminderMilestone.T_MINUS_3.dueDateFor(RUN_DATE)).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(ReminderMilestone.DUE_TODAY.dueDateFor(RUN_DATE)).isEqualTo(RUN_DATE);
        assertThat(ReminderMilestone.OVERDUE_1.dueDateFor(RUN_DATE)).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(ReminderMilestone.OVERDUE_7.dueDateFor(RUN_DATE)).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void atMatchesAnEntryToTheMilestoneItSitsOn() {
        assertThat(ReminderMilestone.at(RUN_DATE, LocalDate.of(2026, 9, 8)))
                .contains(ReminderMilestone.T_MINUS_7);
        assertThat(ReminderMilestone.at(RUN_DATE, RUN_DATE))
                .contains(ReminderMilestone.DUE_TODAY);
        assertThat(ReminderMilestone.at(RUN_DATE, LocalDate.of(2026, 8, 25)))
                .contains(ReminderMilestone.OVERDUE_7);
    }

    /**
     * The behaviour that stops the system texting somebody every single day.
     * Four days overdue is not "nearly three" and not "nearly seven" — it is
     * a day on which we say nothing.
     */
    @Test
    void atReturnsEmptyForDaysThatSitBetweenMilestones() {
        assertThat(ReminderMilestone.at(RUN_DATE, LocalDate.of(2026, 8, 28))).isEmpty(); // +4
        assertThat(ReminderMilestone.at(RUN_DATE, LocalDate.of(2026, 8, 30))).isEmpty(); // +2
        assertThat(ReminderMilestone.at(RUN_DATE, LocalDate.of(2026, 9, 6))).isEmpty();  // -5
    }

    @Test
    void atReturnsEmptyWellOutsideTheCadenceInBothDirections() {
        assertThat(ReminderMilestone.at(RUN_DATE, RUN_DATE.plusMonths(3))).isEmpty();
        assertThat(ReminderMilestone.at(RUN_DATE, RUN_DATE.minusYears(2))).isEmpty();
    }

    @Test
    void sweepWindowSpansEveryMilestoneAndNothingMore() {
        assertThat(ReminderMilestone.earliestDueDateOfInterest(RUN_DATE))
                .isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(ReminderMilestone.latestDueDateOfInterest(RUN_DATE))
                .isEqualTo(LocalDate.of(2026, 9, 8));
    }

    /**
     * Guards the window against a new milestone being added without the
     * bounds following it — the failure mode being a milestone that silently
     * never fires because the sweep does not fetch its rows.
     */
    @Test
    void everyMilestoneFallsInsideTheSweepWindow() {
        LocalDate from = ReminderMilestone.earliestDueDateOfInterest(RUN_DATE);
        LocalDate to = ReminderMilestone.latestDueDateOfInterest(RUN_DATE);

        for (ReminderMilestone milestone : ReminderMilestone.values()) {
            LocalDate dueDate = milestone.dueDateFor(RUN_DATE);
            assertThat(dueDate)
                    .as("due date for %s must be inside the sweep window", milestone)
                    .isBetween(from, to);

            Optional<ReminderMilestone> resolved = ReminderMilestone.at(RUN_DATE, dueDate);
            assertThat(resolved)
                    .as("%s must round-trip through at()", milestone)
                    .contains(milestone);
        }
    }

    @Test
    void preAndPostDueClassificationMatchesTheOffsetSign() {
        assertThat(ReminderMilestone.T_MINUS_7.isPreDue()).isTrue();
        assertThat(ReminderMilestone.T_MINUS_7.isPostDue()).isFalse();

        assertThat(ReminderMilestone.DUE_TODAY.isPreDue()).isFalse();
        assertThat(ReminderMilestone.DUE_TODAY.isPostDue()).isFalse();

        assertThat(ReminderMilestone.OVERDUE_3.isPostDue()).isTrue();
        assertThat(ReminderMilestone.OVERDUE_3.isPreDue()).isFalse();
    }
}
