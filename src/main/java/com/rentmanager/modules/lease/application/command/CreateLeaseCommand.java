package com.rentmanager.modules.lease.application.command;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A dumb data carrier. No logic, no validation.
 * Represents the intent to create a lease — nothing more.
 * The handler decides what to do with it.
 */
public record CreateLeaseCommand(

        UUID tenantId,
        UUID propertyId,
        UUID unitId,
        UUID tenantProfileId,

        LeaseType leaseType,
        BillingCycle billingCycle,

        LocalDate startDate,
        LocalDate endDate,

        BigDecimal monthlyRent,
        BigDecimal securityDeposit,
        BigDecimal lateFeeAmount,

        Integer gracePeriodDays,
        boolean autoRenew

) {}