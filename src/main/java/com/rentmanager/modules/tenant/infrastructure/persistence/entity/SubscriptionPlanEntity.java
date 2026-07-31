package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Platform-global subscription tier catalog. Deliberately matches the
 * domain {@code SubscriptionPlan} one-for-one (previously this entity
 * carried a tenant_id + plan_code + single-price schema that contradicted
 * the global-catalog domain model and dropped monthly/yearly prices on
 * every round-trip; the V50 migration created the corrected table from
 * scratch since no migration had ever created this table).
 */
@Getter
@Setter
@Entity
@Table(
        name = "subscription_plans",
        indexes = {
                @Index(name = "idx_subscription_plans_active", columnList = "active"),
                @Index(name = "idx_subscription_plans_self_service", columnList = "self_service")
        }
)
@NoArgsConstructor
public class SubscriptionPlanEntity extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 50)
    private BillingCycle billingCycle;

    @Column(name = "max_units")
    private Integer maxUnits;

    @Column(name = "monthly_price", precision = 19, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "yearly_price", precision = 19, scale = 2)
    private BigDecimal yearlyPrice;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "self_service", nullable = false)
    private boolean selfService;
}
