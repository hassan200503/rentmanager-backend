package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A dated entry in the landlord Monthly Rental Income rate schedule.
 *
 * <p>Invariants:
 * <ul>
 *   <li>Only the ACTIVE row with the latest {@code effectiveFrom} is ever
 *       read for computation — enforced by the repository query, not by
 *       callers.</li>
 *   <li>SCHEDULED rows (pre-staged, unverified rates) are never used in
 *       computation until flipped to ACTIVE by the rate-policy service.</li>
 *   <li>ratePercent is a decimal fraction: 0.0750 = 7.5%.</li>
 * </ul>
 */
public class MriRateSchedule extends AggregateRoot {

    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private BigDecimal ratePercent;
    private MriRateScheduleStatus status;
    private String sourceReference;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    protected MriRateSchedule() {
    }

    public static MriRateSchedule schedule(
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal ratePercent,
            String sourceReference
    ) {
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("effectiveFrom is required");
        }
        if (ratePercent == null || ratePercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("ratePercent must be positive");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot precede effectiveFrom");
        }

        MriRateSchedule schedule = new MriRateSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.effectiveFrom = effectiveFrom;
        schedule.effectiveTo = effectiveTo;
        schedule.ratePercent = ratePercent;
        schedule.status = MriRateScheduleStatus.SCHEDULED;
        schedule.sourceReference = sourceReference;
        return schedule;
    }

    /**
     * Promotes a pre-staged rate to ACTIVE. Only SCHEDULED rows can be
     * activated; re-activating the current ACTIVE row is a no-op.
     */
    public void activate() {
        if (status == MriRateScheduleStatus.ACTIVE) {
            return;
        }
        if (status != MriRateScheduleStatus.SCHEDULED) {
            throw new IllegalStateException("Only SCHEDULED rates can be activated, was " + status);
        }
        this.status = MriRateScheduleStatus.ACTIVE;
    }

    /**
     * Closes off this rate as SUPERSEDED, e.g. when a newer rate is
     * activated. Marks the schedule as ended at the given date.
     */
    public void supersede(LocalDate effectiveTo) {
        if (status == MriRateScheduleStatus.SUPERSEDED) {
            return;
        }
        if (effectiveTo != null && effectiveFrom != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot precede effectiveFrom");
        }
        this.status = MriRateScheduleStatus.SUPERSEDED;
        this.effectiveTo = effectiveTo;
    }

    public boolean isEffectiveOn(LocalDate date) {
        if (date == null || status != MriRateScheduleStatus.ACTIVE) {
            return false;
        }
        if (date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }

    public static MriRateSchedule rehydrate(
            UUID id,
            UUID tenantId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            BigDecimal ratePercent,
            MriRateScheduleStatus status,
            String sourceReference,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        MriRateSchedule schedule = new MriRateSchedule();
        schedule.setId(id);
        if (tenantId != null) {
            schedule.restoreTenantId(tenantId);
        }
        schedule.effectiveFrom = effectiveFrom;
        schedule.effectiveTo = effectiveTo;
        schedule.ratePercent = ratePercent;
        schedule.status = status;
        schedule.sourceReference = sourceReference;
        schedule.version = version;
        schedule.createdAt = createdAt;
        schedule.updatedAt = updatedAt;
        return schedule;
    }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public MriRateScheduleStatus getStatus() { return status; }
    public String getSourceReference() { return sourceReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
