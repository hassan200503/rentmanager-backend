package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.tax.domain.enums.MonthlyFilingStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A landlord's Monthly Rental Income filing for one month (eRITS).
 *
 * <p>{@code period} is the first day of the filing month. A landlord with
 * no residential payments in the month gets a NIL filing (isNilReturn
 * TRUE, amounts zero) — eRITS accepts a nil return in lieu of a zero-value
 * filing. mriTaxDue = grossRentalIncome x mriRateApplied.
 *
 * <p>Lifecycle: COMPUTED -> READY_FOR_MANUAL | TRANSMITTED | FAILED.
 */
public class MonthlyRentalIncomeFiling extends AggregateRoot {

    private LocalDate period;
    private BigDecimal grossRentalIncome;
    private boolean isNilReturn;
    private BigDecimal mriRateApplied;
    private BigDecimal mriTaxDue;
    private MonthlyFilingStatus status;
    private LocalDateTime computedAt;
    private LocalDateTime filedAt;
    private LocalDateTime transmittedAt;
    private int attemptCount;
    private LocalDateTime nextAttemptAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    protected MonthlyRentalIncomeFiling() {
    }

    /**
     * Computes the landlord's filing for the given month.
     *
     * @param grossRentalIncome sum of residential rent payments in the month
     *                          (document currency, 2dp); 0 produces a nil return.
     * @param mriRateApplied    the ACTIVE MRI rate for the month (decimal
     *                          fraction, e.g. 0.0750).
     */
    public static MonthlyRentalIncomeFiling compute(
            UUID landlordTenantId,
            LocalDate period,
            BigDecimal grossRentalIncome,
            BigDecimal mriRateApplied
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (period == null || period.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("period must be the first day of a month");
        }
        if (mriRateApplied == null || mriRateApplied.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("mriRateApplied must be positive");
        }

        BigDecimal gross = grossRentalIncome != null ? grossRentalIncome : BigDecimal.ZERO;
        gross = gross.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxDue = gross.multiply(mriRateApplied).setScale(2, RoundingMode.HALF_UP);

        MonthlyRentalIncomeFiling filing = new MonthlyRentalIncomeFiling();
        filing.setId(UUID.randomUUID());
        filing.assignTenant(landlordTenantId);
        filing.period = period;
        filing.grossRentalIncome = gross;
        filing.isNilReturn = gross.compareTo(BigDecimal.ZERO) == 0;
        filing.mriRateApplied = mriRateApplied;
        filing.mriTaxDue = taxDue;
        filing.status = MonthlyFilingStatus.COMPUTED;
        filing.computedAt = LocalDateTime.now();
        filing.attemptCount = 0;
        return filing;
    }

    public void markReadyForManual() {
        this.status = MonthlyFilingStatus.READY_FOR_MANUAL;
        this.nextAttemptAt = null;
    }

    public void markTransmitted() {
        if (status == MonthlyFilingStatus.TRANSMITTED) {
            return;
        }
        this.status = MonthlyFilingStatus.TRANSMITTED;
        this.transmittedAt = LocalDateTime.now();
        this.filedAt = this.transmittedAt;
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    public void markTransmissionFailure(String error, int maxAttempts) {
        if (status == MonthlyFilingStatus.TRANSMITTED) {
            return;
        }
        this.status = MonthlyFilingStatus.FAILED;
        this.lastError = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        this.attemptCount++;
        this.nextAttemptAt = this.attemptCount < maxAttempts
                ? LocalDateTime.now().plusMinutes(30L * this.attemptCount)
                : null;
    }

    public boolean isTransmissionCandidate(LocalDateTime now, int maxAttempts) {
        if (status != MonthlyFilingStatus.COMPUTED && status != MonthlyFilingStatus.FAILED) {
            return false;
        }
        if (attemptCount >= maxAttempts || nextAttemptAt == null) {
            return false;
        }
        return !nextAttemptAt.isAfter(now);
    }

    public static MonthlyRentalIncomeFiling rehydrate(
            UUID id,
            UUID tenantId,
            LocalDate period,
            BigDecimal grossRentalIncome,
            boolean isNilReturn,
            BigDecimal mriRateApplied,
            BigDecimal mriTaxDue,
            MonthlyFilingStatus status,
            LocalDateTime computedAt,
            LocalDateTime filedAt,
            LocalDateTime transmittedAt,
            int attemptCount,
            LocalDateTime nextAttemptAt,
            String lastError,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        MonthlyRentalIncomeFiling filing = new MonthlyRentalIncomeFiling();
        filing.setId(id);
        filing.restoreTenantId(tenantId);
        filing.period = period;
        filing.grossRentalIncome = grossRentalIncome;
        filing.isNilReturn = isNilReturn;
        filing.mriRateApplied = mriRateApplied;
        filing.mriTaxDue = mriTaxDue;
        filing.status = status;
        filing.computedAt = computedAt;
        filing.filedAt = filedAt;
        filing.transmittedAt = transmittedAt;
        filing.attemptCount = attemptCount;
        filing.nextAttemptAt = nextAttemptAt;
        filing.lastError = lastError;
        filing.version = version;
        filing.createdAt = createdAt;
        filing.updatedAt = updatedAt;
        return filing;
    }

    public LocalDate getPeriod() { return period; }
    public BigDecimal getGrossRentalIncome() { return grossRentalIncome; }
    public boolean isNilReturn() { return isNilReturn; }
    public BigDecimal getMriRateApplied() { return mriRateApplied; }
    public BigDecimal getMriTaxDue() { return mriTaxDue; }
    public MonthlyFilingStatus getStatus() { return status; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public LocalDateTime getFiledAt() { return filedAt; }
    public LocalDateTime getTransmittedAt() { return transmittedAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}