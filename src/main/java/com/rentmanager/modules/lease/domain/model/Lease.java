package com.rentmanager.modules.lease.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.lease.domain.enums.*;
import com.rentmanager.modules.lease.domain.event.*;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;



public class Lease extends AggregateRoot {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tenant_profile_id", nullable = false)
    private UUID tenantProfileId;

    @Column(name = "lease_number", nullable = false, unique = true)
    private String leaseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "lease_type", nullable = false)
    private LeaseType leaseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private BillingCycle billingCycle;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "monthly_rent", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyRent;

    @Column(name = "security_deposit", precision = 19, scale = 2)
    private BigDecimal securityDeposit;

    @Column(name = "late_fee_amount", precision = 19, scale = 2)
    private BigDecimal lateFeeAmount;

    @Column(name = "grace_period_days")
    private Integer gracePeriodDays;

    @Column(name = "auto_renew", nullable = false)
    private boolean autoRenew;

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
    @Column(name = "termination_type")
    private TerminationType terminationType;

    @Column(name = "termination_reason", length = 1000)
    private String terminationReason;

    protected Lease() {
    }

    public static Lease create(
            UUID tenantId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            String leaseNumber,
            LeaseType leaseType,
            BillingCycle billingCycle,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRent,
            BigDecimal securityDeposit,
            BigDecimal lateFeeAmount,
            Integer gracePeriodDays,
            boolean autoRenew

    ) {

        Lease lease = new Lease();

        if (tenantId == null)
            throw new LeaseStateException(
                    "tenantId cannot be null",
                    ErrorCode.LEASE_TENANT_NULL
            );

        if (propertyId == null)
            throw new LeaseStateException(
                    "propertyId cannot be null",
                    ErrorCode.LEASE_PROPERTY_NULL
            );

        if (unitId == null)
            throw new LeaseStateException(
                    "unitId cannot be null",
                    ErrorCode.LEASE_UNIT_NULL
            );

        if (tenantProfileId == null)
            throw new LeaseStateException(
                    "tenantProfileId cannot be null",
                    ErrorCode.LEASE_PROFILE_NULL
            );

        if (leaseNumber == null || leaseNumber.isBlank())
            throw new LeaseStateException(
                    "leaseNumber cannot be blank",
                    ErrorCode.LEASE_NUMBER_BLANK
            );

        if (monthlyRent == null || monthlyRent.compareTo(BigDecimal.ZERO) <= 0)
            throw new LeaseStateException(
                    "monthlyRent must be > 0",
                    ErrorCode.LEASE_INVALID_RENT
            );

        if (securityDeposit == null || securityDeposit.compareTo(BigDecimal.ZERO) < 0)
            throw new LeaseStateException(
                    "securityDeposit must be >= 0",
                    ErrorCode.LEASE_INVALID_DEPOSIT
            );

        if (startDate == null || endDate == null || !endDate.isAfter(startDate))
            throw new LeaseStateException(
                    "Invalid date range",
                    ErrorCode.LEASE_INVALID_DATE_RANGE
            );

        lease.setId(UUID.randomUUID());
        lease.assignTenant(tenantId);

        lease.propertyId = propertyId;
        lease.unitId = unitId;
        lease.tenantProfileId = tenantProfileId;

        lease.leaseNumber = leaseNumber;
        lease.leaseType = leaseType;
        lease.billingCycle = billingCycle;

        lease.monthlyRent = monthlyRent;
        lease.securityDeposit = securityDeposit;
        lease.lateFeeAmount = lateFeeAmount;
        lease.gracePeriodDays = gracePeriodDays;
        lease.autoRenew = autoRenew;

        lease.startDate = startDate;
        lease.endDate = endDate;

        lease.status = LeaseStatus.DRAFT;

        lease.registerEvent(
                new LeaseCreatedEvent(
                        tenantId,
                        lease.getId(),
                        "SYSTEM",
                        propertyId,
                        unitId,
                        tenantProfileId,
                        LeaseStatus.DRAFT
                )

        );

        return lease;
    }

    public void approve() {

        if (status != LeaseStatus.DRAFT) {
            throw new LeaseStateException(
                    "Only draft leases can be approved",
                    ErrorCode.LEASE_APPROVAL_ONLY_DRAFT_ALLOWED
            );
        }
        status = LeaseStatus.PENDING_APPROVAL;

        registerEvent(
                new LeaseApprovedEvent(
                        getTenantId(),
                        getId(),
                        "SYSTEM",
                        propertyId,
                        unitId,
                        tenantProfileId
                )
        );
    }

    public void markAwaitingDeposit() {
        if (status != LeaseStatus.PENDING_APPROVAL) {
            throw new LeaseStateException(
                    "Only pending-approval leases can move to awaiting deposit",
                    ErrorCode.LEASE_AWAITING_DEPOSIT_ONLY_PENDING_ALLOWED
            );
        }
        status = LeaseStatus.AWAITING_DEPOSIT;

        registerEvent(new LeaseAwaitingDepositEvent(
                getTenantId(), getId(), "SYSTEM", propertyId, unitId, tenantProfileId
        ));
    }

    public void activate() {

        if (status != LeaseStatus.AWAITING_DEPOSIT) {
            throw new LeaseStateException(
                    "Only leases awaiting deposit can be activated",
                    ErrorCode.LEASE_ACTIVATION_ONLY_AWAITING_DEPOSIT_ALLOWED
            );
        }
        status = LeaseStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();

        registerEvent(
                new LeaseActivatedEvent(
                        getTenantId(),
                        getId(),
                        "SYSTEM",
                        propertyId,
                        unitId,
                        tenantProfileId
                )
        );
    }

    public void reject(String reason) {

        if (status != LeaseStatus.DRAFT && status != LeaseStatus.PENDING_APPROVAL) {
            throw new LeaseStateException(
                    "Only draft or pending approval leases can be rejected",
                    ErrorCode.LEASE_REJECTION_ONLY_DRAFT_OR_PENDING_ALLOWED
            );
        }

        this.status = LeaseStatus.TERMINATED;
        this.terminatedAt = LocalDateTime.now();
        this.terminationReason = reason;

        registerEvent(new LeaseCancelledEvent(
                getTenantId(),
                getId(),
                "SYSTEM",
                propertyId,
                unitId,
                tenantProfileId,
                reason
        ));
    }

    public void terminate(TerminationType type, String reason, String actor, UUID tenantId) {
        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.RENEWED) {
            throw new LeaseStateException(
                    "Only active or renewed leases can be terminated",
                    ErrorCode.LEASE_TERMINATION_ONLY_ACTIVE_ALLOWED
            );
        }

        if (type == null) {
            throw new LeaseStateException(
                    "Termination type cannot be null",
                    ErrorCode.LEASE_TERMINATION_TYPE_REQUIRED
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new LeaseStateException(
                    "Termination reason cannot be empty",
                    ErrorCode.LEASE_TERMINATION_REASON_REQUIRED
            );
        }

        if (actor == null || actor.isBlank()) {
            throw new LeaseStateException(
                    "Actor cannot be null",
                    ErrorCode.LEASE_TERMINATION_ACTOR_REQUIRED
            );
        }

        if (tenantId == null) {
            throw new LeaseStateException(
                    "TenantId cannot be null",
                    ErrorCode.LEASE_TERMINATION_TENANT_REQUIRED
            );
        }

        this.status = LeaseStatus.TERMINATED;
        this.terminatedAt = LocalDateTime.now();
        this.terminationType = type;
        this.terminationReason = reason;

        registerEvent(new LeaseTerminatedEvent(
                tenantId,
                getId(),
                actor,
                this.propertyId,
                this.unitId,
                this.tenantProfileId,
                type,
                reason
        ));
    }




    public void cancel(String reason) {

        // Previously only ACTIVE was refused, so an occupied RENEWED lease, or
        // one already TERMINATED/EXPIRED/CANCELLED, could be cancelled.
        if (!status.isCancellable()) {
            throw new LeaseStateException(
                    "Only a lease that has not started can be cancelled. End an active or renewed lease by terminating it.",
                    ErrorCode.LEASE_UPDATE_CLOSED_NOT_ALLOWED
            );
        }
        // UPDATED (§4.3, V34 migration): now sets its own dedicated
        // cancelledAt timestamp instead of reusing terminatedAt. Was:
        // this.terminatedAt = LocalDateTime.now();
        this.status = LeaseStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.terminationReason = reason;

        registerEvent(new LeaseCancelledEvent(
                getTenantId(),
                getId(),
                "SYSTEM",
                propertyId,
                unitId,
                tenantProfileId,
                reason
        ));
    }





    public void expire() {
        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.RENEWED) {
            throw new LeaseStateException(
                    "Only active or renewed leases can expire",
                    ErrorCode.LEASE_EXPIRATION_ONLY_ACTIVE_ALLOWED
            );
        }
        this.status = LeaseStatus.EXPIRED;
        this.expiredAt = LocalDateTime.now();

        // FIX (this session): restored. Previously omitted deliberately to
        // dodge a double-publish through LeaseWorkflowEngine.expire()'s
        // direct eventPublisher.publish() call — that direct call has now
        // been removed from the engine (see LeaseWorkflowEngine.java), so
        // this registerEvent() is once again the sole publish path for
        // LeaseExpiredEvent, consistent with every other transition on this
        // aggregate. LeaseActionScheduler.expireOne() was updated in the
        // same change to pull and publish this event, since it previously
        // relied entirely on the engine's now-removed direct publish.
        registerEvent(
                new LeaseExpiredEvent(
                        getTenantId(),
                        getId(),
                        "SYSTEM",
                        propertyId,
                        unitId,
                        tenantProfileId
                )
        );
    }

    public void renew(
            LocalDate newStart,
            LocalDate newEnd,
            UUID tenantId,
            String actor
    ) {

        if (status != LeaseStatus.ACTIVE && status != LeaseStatus.EXPIRED && status != LeaseStatus.RENEWED) {
            throw new LeaseStateException(
                    "Only active, expired, or renewed leases can be renewed",
                    ErrorCode.LEASE_RENEWAL_ONLY_ACTIVE_OR_EXPIRED_ALLOWED
            );
        }

        if (newStart == null || newEnd == null) {
            throw new LeaseStateException(
                    "Renewal dates cannot be null",
                    ErrorCode.LEASE_RENEWAL_START_DATE_REQUIRED
            );
        }

        if (newEnd.isBefore(newStart) || newEnd.isEqual(newStart)) {
            throw new LeaseStateException(
                    "End date must be after start date",
                    ErrorCode.LEASE_RENEWAL_INVALID_DATE_RANGE
            );
        }

        if (tenantId == null) {
            throw new LeaseStateException(
                    "TenantId cannot be null",
                    ErrorCode.LEASE_RENEWAL_TENANT_REQUIRED
            );
        }

        if (actor == null || actor.isBlank()) {
            throw new LeaseStateException(
                    "Actor cannot be null",
                    ErrorCode.LEASE_RENEWAL_ACTOR_REQUIRED
            );
        }

        this.startDate = newStart;
        this.endDate = newEnd;

        this.status = LeaseStatus.RENEWED;
        this.renewedAt = LocalDateTime.now();

        registerEvent(new LeaseRenewedEvent(
                tenantId,
                getId(),
                actor,
                newStart,
                newEnd
        ));
    }

    public String getLeaseNumber() {
        return leaseNumber;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }

    public LeaseType getLeaseType() {
        return leaseType;
    }

    public BillingCycle getBillingCycle() {
        return billingCycle;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getRentAmount() {
        return monthlyRent;
    }

    public BigDecimal getSecurityDeposit() {
        return securityDeposit;
    }

    public BigDecimal getLateFeeAmount() {
        return lateFeeAmount;
    }

    public Integer getGracePeriodDays() {
        return gracePeriodDays;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public LeaseStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return this.status == LeaseStatus.ACTIVE;
    }

    public boolean isExpired() {
        return this.status == LeaseStatus.EXPIRED;
    }

    public boolean isTerminated() {
        return this.status == LeaseStatus.TERMINATED;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public LocalDateTime getSignedAt() {
        return signedAt;
    }

    public LocalDateTime getActivatedAt() {
        return activatedAt;
    }

    public LocalDateTime getTerminatedAt() {
        return terminatedAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public LocalDateTime getRenewedAt() {
        return renewedAt;
    }

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTenantProfileId(UUID tenantProfileId) {
        this.tenantProfileId = tenantProfileId;
    }

    public void setStatus(LeaseStatus status) {
        this.status = status;
    }

    public void updateContractTerms(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal rentAmount,
            BigDecimal securityDeposit,
            BigDecimal lateFeeAmount,
            Integer gracePeriodDays,
            boolean autoRenew
    ) {

        if (status.isTerminated() || status.isCancelled()) {
            throw new LeaseStateException(
                    "Cannot update a closed lease",
                    ErrorCode.LEASE_UPDATE_CLOSED_NOT_ALLOWED
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new LeaseStateException(
                    "End date cannot be before start date",
                    ErrorCode.LEASE_UPDATE_INVALID_DATE_RANGE
            );
        }

        validateAmount(rentAmount);
        validateAmount(securityDeposit);
        validateAmount(lateFeeAmount);

        this.startDate = startDate;
        this.endDate = endDate;
        this.monthlyRent = rentAmount;
        this.securityDeposit = securityDeposit;
        this.lateFeeAmount = lateFeeAmount;
        this.gracePeriodDays = gracePeriodDays;
        this.autoRenew = autoRenew;
    }

    private void validateAmount(java.math.BigDecimal amount) {
        if (amount == null) {
            throw new LeaseStateException(
                    "Amount cannot be null",
                    ErrorCode.LEASE_AMOUNT_REQUIRED
            );
        }

        if (amount.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new LeaseStateException(
                    "Amount must be >= 0",
                    ErrorCode.LEASE_AMOUNT_MUST_BE_NON_NEGATIVE
            );
        }
    }

    // NOT MODIFIED IN THIS PASS — see chat: this factory only reconstructs
    // 8 of the aggregate's 19 persisted fields (missing lateFeeAmount,
    // gracePeriodDays, autoRenew, signedAt, activatedAt, terminatedAt,
    // expiredAt, renewedAt, cancelledAt, terminationType,
    // terminationReason). Left untouched pending review of the mapper that
    // calls this, to confirm the call site and avoid a blind signature
    // change on a live persistence path.
    public static Lease restore(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            String leaseNumber,
            LeaseType leaseType,
            BillingCycle billingCycle,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal rentAmount,
            BigDecimal securityDeposit,
            LeaseStatus status
    ) {
        Lease lease = new Lease();

        lease.setId(id);
        lease.assignTenant(tenantId);
        lease.propertyId = propertyId;
        lease.unitId = unitId;
        lease.tenantProfileId = tenantProfileId;
        lease.leaseNumber = leaseNumber;
        lease.leaseType = leaseType;
        lease.billingCycle = billingCycle;
        lease.startDate = startDate;
        lease.endDate = endDate;
        lease.monthlyRent = rentAmount;
        lease.securityDeposit = securityDeposit;
        lease.status = status;

        return lease;
    }




    public void activatePending() {

        if (status != LeaseStatus.PENDING_ACTIVATION) {
            throw new LeaseStateException(
                    "Only leases pending activation can be activated",
                    ErrorCode.LEASE_ACTIVATION_ONLY_PENDING_ACTIVATION_ALLOWED
            );
        }

        this.status = LeaseStatus.ACTIVE;
        this.activatedAt = LocalDateTime.now();

        registerEvent(
                new LeaseActivatedEvent(
                        getTenantId(),
                        getId(),
                        "SYSTEM",
                        propertyId,
                        unitId,
                        tenantProfileId
                )
        );
    }




    public static Lease createPendingActivation(
            UUID tenantId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            String leaseNumber,
            LeaseType leaseType,
            BillingCycle billingCycle,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal monthlyRent,
            BigDecimal securityDeposit,
            BigDecimal lateFeeAmount,
            Integer gracePeriodDays,
            boolean autoRenew
    ) {
        Lease lease = create(tenantId, propertyId, unitId, tenantProfileId, leaseNumber,
                leaseType, billingCycle, startDate, endDate, monthlyRent,
                securityDeposit, lateFeeAmount, gracePeriodDays, autoRenew);
        lease.status = LeaseStatus.PENDING_ACTIVATION;
        return lease;
    }




}