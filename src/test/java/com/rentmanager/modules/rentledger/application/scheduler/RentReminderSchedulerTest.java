package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.application.reminder.RentReminderService;
import com.rentmanager.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scheduler is now a delegator: it decides <em>when</em>, and
 * {@code RentReminderService} decides <em>what</em>.
 *
 * <p>The behavioural coverage that used to live here — zero balances, missing
 * leases, missing contact details, and one bad entry not aborting the sweep —
 * moved with the logic to {@code RentReminderServiceTest}, where it can be
 * asserted against an explicit run date instead of whatever today happens to
 * be. What remains here is the schedule itself.
 */
class RentReminderSchedulerTest {

    private RentReminderService rentReminderService;
    private RentReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        rentReminderService = mock(RentReminderService.class);
        scheduler = new RentReminderScheduler(rentReminderService, mock(BusinessMetrics.class));
    }

    @Test
    void sweepsForTodayInNairobiTime() {
        when(rentReminderService.sweep(any()))
                .thenReturn(new RentReminderService.SweepResult(0, 0, 0, 0, 0));

        scheduler.sendRentReminders();

        ArgumentCaptor<LocalDate> runDate = ArgumentCaptor.forClass(LocalDate.class);
        verify(rentReminderService).sweep(runDate.capture());

        assertThat(runDate.getValue()).isEqualTo(LocalDate.now(ZoneId.of("Africa/Nairobi")));
    }

    /**
     * A container that comes up in UTC would otherwise fire this at noon
     * Nairobi time, and the run date computed inside would disagree with the
     * trigger that caused it either side of midnight.
     */
    @Test
    void cronIsPinnedToNairobiRatherThanTheServerDefaultZone() throws NoSuchMethodException {
        Method method = RentReminderScheduler.class.getMethod("sendRentReminders");
        org.springframework.scheduling.annotation.Scheduled scheduled =
                method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("Africa/Nairobi");
        assertThat(scheduled.cron()).isEqualTo("0 0 9 * * *");
    }

    @Test
    void reportsTheSweepOutcomeWithoutThrowing() {
        when(rentReminderService.sweep(any()))
                .thenReturn(new RentReminderService.SweepResult(12, 8, 3, 1, 0));

        scheduler.sendRentReminders();

        verify(rentReminderService).sweep(any());
    }
}
