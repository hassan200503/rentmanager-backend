package com.rentmanager.modules.lease.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
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





}