package com.rentmanager.modules.tax.domain.repository;

import com.rentmanager.modules.tax.domain.model.MriRateSchedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MriRateScheduleRepository {

    /**
     * The ACTIVE rate in force on the given date. Returns the single ACTIVE
     * row whose period covers the date, ordered by effective_from.
     */
    Optional<MriRateSchedule> findActiveAsOf(LocalDate date);

    Optional<MriRateSchedule> findById(UUID id);

    List<MriRateSchedule> findAll();

    MriRateSchedule save(MriRateSchedule schedule);
}
