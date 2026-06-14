package com.rentmanager.crossmodule.core;

import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TestDataFactory {

    public Property createProperty(UUID tenantId) {
        return Property.create(
                tenantId,
                "Tower A",
                null,
                null,
                null,
                null,
                "Test Property",
                "TEST"
        );
    }

    public Unit createUnit(UUID tenantId, UUID propertyId) {
        return Unit.create(
                tenantId,
                propertyId,
                "U-101",
                "Unit 101",
                new BigDecimal("1200"),
                "Nice unit",
                "TEST"
        );
    }

    public Lease createLease(UUID tenantId, UUID propertyId, UUID unitId, UUID profileId) {
        return Lease.create(
                tenantId,
                propertyId,
                unitId,
                profileId,
                "L-001",
                LeaseType.FIXED_TERM,
                com.rentmanager.modules.lease.domain.enums.BillingCycle.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusMonths(12),
                new BigDecimal("1200"),
                new BigDecimal("500"),
                new BigDecimal("50"),
                3,
                false
        );
    }
}