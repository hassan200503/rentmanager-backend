package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MriRateScheduleTest {

    @Test
    void schedule_setsScheduledStatusAndDates() {
        MriRateSchedule schedule = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "Finance Act 2026");

        assertEquals(MriRateScheduleStatus.SCHEDULED, schedule.getStatus());
        assertNotNull(schedule.getId());
        assertEquals("Finance Act 2026", schedule.getSourceReference());
        assertEquals(new BigDecimal("0.1000"), schedule.getRatePercent());
    }

    @Test
    void schedule_rejectsInvalidPeriodAndRate() {
        assertThrows(IllegalArgumentException.class, () -> MriRateSchedule.schedule(
                null, null, new BigDecimal("0.0750"), "src"));

        assertThrows(IllegalArgumentException.class, () -> MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null, BigDecimal.ZERO, "src"));

        assertThrows(IllegalArgumentException.class, () -> MriRateSchedule.schedule(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1),
                new BigDecimal("0.0750"), "src"));
    }

    @Test
    void activate_promotesScheduledToActive() {
        MriRateSchedule schedule = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "Finance Act 2026");

        schedule.activate();

        assertEquals(MriRateScheduleStatus.ACTIVE, schedule.getStatus());
    }

    @Test
    void activate_isIdempotentWhenAlreadyActive() {
        MriRateSchedule schedule = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "src");

        schedule.activate();
        schedule.activate();

        assertEquals(MriRateScheduleStatus.ACTIVE, schedule.getStatus());
    }

    @Test
    void activate_rejectsSupersededRate() {
        MriRateSchedule schedule = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "src");
        schedule.supersede(null);

        assertThrows(IllegalStateException.class, schedule::activate);
    }

    @Test
    void supersede_closesTheRate() {
        MriRateSchedule schedule = MriRateSchedule.schedule(
                LocalDate.of(2020, 1, 1), null,
                new BigDecimal("0.0750"), "src");

        schedule.supersede(LocalDate.of(2023, 12, 31));

        assertEquals(MriRateScheduleStatus.SUPERSEDED, schedule.getStatus());
        assertEquals(LocalDate.of(2023, 12, 31), schedule.getEffectiveTo());
    }

    @Test
    void isEffectiveOn_onlyForActiveRatesInsideThePeriod() {
        MriRateSchedule open = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), null,
                new BigDecimal("0.0750"), "Finance Act 2023");
        open.activate();

        assertTrue(open.isEffectiveOn(LocalDate.of(2026, 8, 3)));
        assertTrue(open.isEffectiveOn(LocalDate.of(2024, 1, 1)));
        assertFalse(open.isEffectiveOn(LocalDate.of(2023, 12, 31)));
    }

    @Test
    void scheduledRatesAreNeverEffective() {
        MriRateSchedule scheduled = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "unverified");

        assertFalse(scheduled.isEffectiveOn(LocalDate.of(2026, 8, 3)));
    }

    @Test
    void effectiveOn_respectsClosedEnd() {
        MriRateSchedule closed = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 6, 30),
                new BigDecimal("0.0750"), "src");
        closed.activate();

        assertTrue(closed.isEffectiveOn(LocalDate.of(2025, 1, 1)));
        assertTrue(closed.isEffectiveOn(LocalDate.of(2026, 6, 30)));
        assertFalse(closed.isEffectiveOn(LocalDate.of(2026, 7, 1)));
    }
}