package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderFrequency;
import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Persistence twin of the {@code SubscriptionStandingOrder} aggregate.
 * Tenant id is a plain column (like the domain model, which extends
 * BaseEntity rather than BaseTenantEntity) - tenant isolation at the
 * repository layer is enforced by the caller lookup patterns.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "subscription_standing_orders",
        indexes = {
                @Index(name = "idx_standing_orders_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_standing_orders_status", columnList = "status"),
                @Index(name = "idx_standing_orders_response_ref_id", columnList = "ratiba_response_ref_id")
        }
)
public class SubscriptionStandingOrderJpaEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "account_reference", nullable = false, length = 12)
    private String accountReference;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private StandingOrderFrequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StandingOrderStatus status;

    @Column(name = "ratiba_response_ref_id")
    private String ratibaResponseRefId;

    @Column(name = "ratiba_transaction_id", length = 50)
    private String ratibaTransactionId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "failure_reason")
    private String failureReason;
}
