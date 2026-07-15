package com.rentmanager.modules.lease.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA persistence model for Lease aggregate.
 *
 * IMPORTANT RULES:
 * - This is NOT the domain model
 * - No business logic allowed
 * - Only persistence mapping concerns
 * - Must remain stable for migration safety
 */
@Entity
@Table(
        name = "leases",
        indexes = {
                @Index(name = "idx_lease_tenant", columnList = "tenant_id"),
                @Index(name = "idx_lease_property", columnList = "property_id"),
                @Index(name = "idx_lease_unit", columnList = "unit_id"),
                @Index(name = "idx_lease_status", columnList = "status")
        }
)
public class LeaseEntity extends BaseTenantEntity {

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "tenant_profile_id", nullable = false, updatable = false)
    private UUID tenantProfileId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "rent_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "deposit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LeaseStatus status;

    @Column(name = "lease_number", nullable = false, unique = true)
    private String leaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "lease_type", nullable = false)
    private LeaseType leaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    // ---- Lifecycle metadata (previously orphaned/missing -- see
    // Addendum 4 follow-up finding: activated_at/terminated_at/expired_at
    // existed as columns since V2 but were never mapped; renewed_at/
    // termination_type/termination_reason/signed_at/cancelled_at didn't
    // exist as columns at all until V34. All seven now wired end-to-end. ----

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "renewed_at")
    private LocalDateTime renewedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_type", length = 50)
    private TerminationType terminationType;

    @Column(name = "termination_reason", length = 1000)
    private String terminationReason;

    // ---- FIX (Track B, this session): previously absent entirely — no
    // column, no field, no accessor. Every lease saved before this change
    // silently discarded lateFeeAmount/gracePeriodDays/autoRenew on write,
    // and every reload returned null/null/false regardless of intent.
    // Requires the companion migration (V<next>__add_lease_optional_terms_columns.sql)
    // to run first. autoRenew is NOT NULL DEFAULT FALSE since the domain
    // field is a primitive boolean and can never be null; the other two
    // are nullable to match Lease.create()'s validation (unvalidated,
    // optional fields — unlike monthlyRent/securityDeposit). ----

    @Column(name = "late_fee_amount", precision = 19, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "grace_period_days")
    private Integer gracePeriodDays;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

    // -----------------------------
    // Getters & Setters
    // -----------------------------

    public UUID getPropertyId() {
        return propertyId;
    }

    public void setPropertyId(UUID propertyId) {
        this.propertyId = propertyId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }

    public void setTenantProfileId(UUID tenantProfileId) {
        this.tenantProfileId = tenantProfileId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public BigDecimal getRentAmount() {
        return rentAmount;
    }

    public void setRentAmount(BigDecimal rentAmount) {
        this.rentAmount = rentAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
    }

    public String getLeaseNumber() {
        return leaseNumber;
    }

    public void setLeaseNumber(String leaseNumber) {
        this.leaseNumber = leaseNumber;
    }

    public LeaseType getLeaseType() {
        return leaseType;
    }

    public void setLeaseType(LeaseType leaseType) {
        this.leaseType = leaseType;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(BillingCycle billingCycle) {
        this.billingCycle = billingCycle;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(LocalDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(LocalDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public LocalDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public void setTerminatedAt(LocalDateTime terminatedAt) {
        this.terminatedAt = terminatedAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    public LocalDateTime getRenewedAt() {
        return renewedAt;
    }

    public void setRenewedAt(LocalDateTime renewedAt) {
        this.renewedAt = renewedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public void setTerminationType(TerminationType terminationType) {
        this.terminationType = terminationType;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public BigDecimal getLateFeeAmount() {
        return lateFeeAmount;
    }

    public void setLateFeeAmount(BigDecimal lateFeeAmount) {
        this.lateFeeAmount = lateFeeAmount;
    }

    public Integer getGracePeriodDays() {
        return gracePeriodDays;
    }

    public void setGracePeriodDays(Integer gracePeriodDays) {
        this.gracePeriodDays = gracePeriodDays;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }
}