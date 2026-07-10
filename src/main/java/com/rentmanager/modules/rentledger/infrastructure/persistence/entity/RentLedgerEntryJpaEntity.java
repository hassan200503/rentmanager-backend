package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(
        name = "rent_ledger_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_rent_ledger_entries_lease_period",
                        columnNames = {"lease_id", "billing_period_start"}
                )
        }
)
public class RentLedgerEntryJpaEntity extends BaseTenantEntity {

    @Column(name = "lease_id", nullable = false)
    private java.util.UUID leaseId;

    @Column(name = "unit_id", nullable = false)
    private java.util.UUID unitId;

    @Column(name = "tenant_profile_id", nullable = false)
    private java.util.UUID tenantProfileId;

    @Column(name = "billing_period_start", nullable = false)
    private LocalDate billingPeriodStart;

    @Column(name = "billing_period_end", nullable = false)
    private LocalDate billingPeriodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "amount_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountDue;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RentLedgerStatus status;

    @Column(name = "prorated", nullable = false)
    private boolean prorated;
}