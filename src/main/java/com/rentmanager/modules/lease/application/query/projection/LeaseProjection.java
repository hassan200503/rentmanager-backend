package com.rentmanager.modules.lease.application.query.projection;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * SaaS-grade Lease read projection.
 *
 * READ MODEL ONLY
 * - No domain logic
 * - No persistence annotations
 * - No JPA coupling
 * - CQRS query response model
 */
@Getter
@Setter
@NoArgsConstructor
public class LeaseProjection {

    private UUID leaseId;

    private UUID tenantId;

    private UUID propertyId;

    private UUID unitId;

    private UUID tenantProfileId;

    private String status;

    private BigDecimal rentAmount;

    private BigDecimal securityDeposit;

    private Instant startDate;

    private Instant endDate;

    private Instant createdAt;

    private Instant updatedAt;
}