package com.rentmanager.modules.deposit.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(
        name = "deposits",
        indexes = {
                @Index(name = "idx_deposit_tenant", columnList = "tenant_id"),
                @Index(name = "idx_deposit_lease", columnList = "lease_id"),
                @Index(name = "idx_deposit_status", columnList = "status")
        }
)
@Getter
@Setter
public class DepositJpaEntity extends BaseTenantEntity {

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "tenant_profile_id", nullable = false)
    private UUID tenantProfileId;

    @Column(name = "amount_required", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountRequired;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "amount_refunded", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountRefunded;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DepositStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    public static DepositJpaEntity create(UUID tenantId) {
        DepositJpaEntity entity = new DepositJpaEntity();
        entity.restoreTenantId(tenantId);
        entity.amountPaid = BigDecimal.ZERO;
        entity.amountRefunded = BigDecimal.ZERO;
        entity.status = DepositStatus.UNPAID;
        return entity;
    }
}