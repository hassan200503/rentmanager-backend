package com.rentmanager.crossmodule.core;

import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class TestDataFactory {

    /**
     * Passed null for propertyType, which Property.create rejects with
     * "propertyType is required" — so every cross-module IT that builds a
     * property has been failing at the first line of its fixture. Nobody saw
     * it because those *IT classes never run: surefire's default includes do
     * not match *IT.java and no failsafe plugin is configured (see TD-126).
     */
    public Property createProperty(UUID tenantId) {
        return Property.create(
                tenantId,
                "Tower A",
                com.rentmanager.modules.property.domain.enums.PropertyType.APARTMENT,
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
                null,
                new BigDecimal("1200"),
                null,
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