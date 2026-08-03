package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;
import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.domain.repository.MriRateScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MriRatePolicyServiceTest {

    private MriRateScheduleRepository rateScheduleRepository;
    private MriRatePolicyService service;

    @BeforeEach
    void setUp() {
        rateScheduleRepository = mock(MriRateScheduleRepository.class);
        service = new MriRatePolicyService(rateScheduleRepository);
    }

    @Test
    void activeRateAsOf_returnsActiveRateCoveringTheDate() {
        MriRateSchedule active = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), null,
                new BigDecimal("0.0750"), "Finance Act 2023");
        active.activate();

        when(rateScheduleRepository.findActiveAsOf(LocalDate.of(2026, 8, 3)))
                .thenReturn(Optional.of(active));

        MriRateSchedule found = service.activeRateAsOf(LocalDate.of(2026, 8, 3));

        assertEquals(new BigDecimal("0.0750"), found.getRatePercent());
        assertTrue(found.isEffectiveOn(LocalDate.of(2026, 8, 3)));
    }

    @Test
    void activeRateAsOf_throwsWhenNoActiveRateCoversTheDate() {
        when(rateScheduleRepository.findActiveAsOf(any())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.activeRateAsOf(LocalDate.of(2026, 8, 3)));
    }

    @Test
    void activateRate_promotesAndPersistsScheduledRate() {
        MriRateSchedule scheduled = MriRateSchedule.schedule(
                LocalDate.of(2026, 7, 1), null,
                new BigDecimal("0.1000"), "Finance Act 2026");
        when(rateScheduleRepository.findById(scheduled.getId()))
                .thenReturn(Optional.of(scheduled));
        when(rateScheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.activateRate(scheduled.getId());

        assertEquals(MriRateScheduleStatus.ACTIVE, scheduled.getStatus());
        verify(rateScheduleRepository).save(scheduled);
    }

    @Test
    void activateRate_noOpOnAlreadyActiveRate() {
        MriRateSchedule active = MriRateSchedule.schedule(
                LocalDate.of(2024, 1, 1), null,
                new BigDecimal("0.0750"), "Finance Act 2023");
        active.activate();
        when(rateScheduleRepository.findById(active.getId())).thenReturn(Optional.of(active));

        service.activateRate(active.getId());

        verify(rateScheduleRepository, never()).save(any());
    }

    @Test
    void activateRate_throwsForUnknownRate() {
        when(rateScheduleRepository.findById(UUID.randomUUID())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.activateRate(UUID.randomUUID()));
    }
}