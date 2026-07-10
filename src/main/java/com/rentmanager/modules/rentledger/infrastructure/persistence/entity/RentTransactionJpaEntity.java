package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "rent_transactions")
public class RentTransactionJpaEntity extends BaseTenantEntity {

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "lease_id", nullable = false)
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RentTransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "external_reference")
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private RentTransactionSource source;

    @Column(name = "recorded_by", nullable = false)
    private String recordedBy;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;
}