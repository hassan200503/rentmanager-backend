package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "commission_policies")
public class CommissionPolicyJpaEntity extends BaseEntity {

    @Column(name = "landlord_org_id")
    private UUID landlordOrgId;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false, length = 200)
    private String createdBy;
}
