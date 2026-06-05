package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(
        name = "subscription_plans",
        indexes = {
                @Index(name = "idx_plan_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_plan_code", columnList = "plan_code")
        }
)
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "billing_interval", nullable = false, length = 30)
    private String billingInterval;

    @Column(name = "max_properties")
    private Integer maxProperties;

    @Column(name = "max_units")
    private Integer maxUnits;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPlan;

    @Column(name = "created_by")
    private UUID createdBy;
}