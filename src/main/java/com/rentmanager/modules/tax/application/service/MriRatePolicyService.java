package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;
import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.domain.repository.MriRateScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Policy around the Monthly Rental Income rate.
 *
 * <p>Computational rule: use the single ACTIVE rate in force for the
 * filing date. Pre-staged SCHEDULED rows are never used for computation —
 * they must be human-verified and activated first. This protects the
 * system against computing an unverified statutory change (currently the
 * UNCONFIRMED Finance Act 2026 proposal to revert MRI to 10% on
 * 1 Jul 2026 — brief item A1).
 */
@Service
@RequiredArgsConstructor
public class MriRatePolicyService {

    private final MriRateScheduleRepository rateScheduleRepository;

    /**
     * The ACTIVE MRI rate in force on the given date.
     *
     * @throws IllegalStateException when no ACTIVE rate covers the date —
     *         the pipeline must never guess a rate.
     */
    public MriRateSchedule activeRateAsOf(LocalDate date) {
        return rateScheduleRepository.findActiveAsOf(date)
                .orElseThrow(() -> new IllegalStateException(
                        "No active MRI rate in force on " + date));
    }

    /**
     * Activates a pre-staged (SCHEDULED) rate after human verification.
     */
    public void activateRate(java.util.UUID rateId) {
        MriRateSchedule rate = rateScheduleRepository.findById(rateId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown rate schedule: " + rateId));
        if (rate.getStatus() != MriRateScheduleStatus.ACTIVE) {
            rate.activate();
            rateScheduleRepository.save(rate);
        }
    }
}