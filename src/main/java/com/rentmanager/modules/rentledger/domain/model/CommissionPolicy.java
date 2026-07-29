package com.rentmanager.modules.rentledger.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CommissionPolicy {

    private UUID id;
    private Long version;
    private UUID landlordOrgId;
    private BigDecimal ratePercent;
    private Instant effectiveFrom;
    private boolean active;
    private String createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    private CommissionPolicy() {}

    private CommissionPolicy(BigDecimal ratePercent, Instant effectiveFrom, String createdBy, UUID landlordOrgId) {
        this.id = UUID.randomUUID();
        this.ratePercent = ratePercent;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.landlordOrgId = landlordOrgId;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public static CommissionPolicy create(BigDecimal ratePercent, Instant effectiveFrom, String createdBy) {
        return new CommissionPolicy(ratePercent, effectiveFrom, createdBy, null);
    }

    public static CommissionPolicy createForLandlord(BigDecimal ratePercent, Instant effectiveFrom, String createdBy, UUID landlordOrgId) {
        return new CommissionPolicy(ratePercent, effectiveFrom, createdBy, landlordOrgId);
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public static CommissionPolicy rehydrate(
            UUID id, Long version, UUID landlordOrgId, BigDecimal ratePercent,
            Instant effectiveFrom, boolean active, String createdBy,
            Instant createdAt, Instant updatedAt
    ) {
        CommissionPolicy p = new CommissionPolicy();
        p.id = id;
        p.version = version;
        p.landlordOrgId = landlordOrgId;
        p.ratePercent = ratePercent;
        p.effectiveFrom = effectiveFrom;
        p.active = active;
        p.createdBy = createdBy;
        p.createdAt = createdAt;
        p.updatedAt = updatedAt;
        return p;
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public UUID getLandlordOrgId() { return landlordOrgId; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public boolean isActive() { return active; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
