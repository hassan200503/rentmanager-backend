package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenant_subscriptions",
        indexes = {
                @Index(name = "idx_subscription_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_subscription_plan_id", columnList = "subscription_plan_id"),
                @Index(name = "idx_subscription_status", columnList = "status")
        }
)
@NoArgsConstructor
public class TenantSubscriptionEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    // ----------------------------------------------------------------
    // RELATIONSHIPS (SOFT, NOT JPA FK YET)
    // ----------------------------------------------------------------

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_plan_id", nullable = false)
    private UUID subscriptionPlanId;

    // ----------------------------------------------------------------
    // LIFECYCLE STATE
    // ----------------------------------------------------------------

    @Column(name = "status", nullable = false, length = 50)
    private String status; 
    // TRIAL, ACTIVE, SUSPENDED, CANCELLED, EXPIRED

    // ----------------------------------------------------------------
    // BILLING INFO
    // ----------------------------------------------------------------

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "trial_end_date")
    private Instant trialEndDate;

    // ----------------------------------------------------------------
    // BILLING CONFIG SNAPSHOT
    // ----------------------------------------------------------------

    @Column(name = "price_snapshot")
    private Double priceSnapshot;

    @Column(name = "currency_snapshot", length = 10)
    private String currencySnapshot;

    @Column(name = "billing_interval", length = 20)
    private String billingInterval;

    // ----------------------------------------------------------------
    // LIMIT SNAPSHOT (IMPORTANT FOR SAAS ENFORCEMENT)
    // ----------------------------------------------------------------

    @Column(name = "max_properties")
    private Integer maxProperties;

    @Column(name = "max_units")
    private Integer maxUnits;

    @Column(name = "max_users")
    private Integer maxUsers;

    // ----------------------------------------------------------------
    // AUDIT
    // ----------------------------------------------------------------

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;
}