package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LeaseCreationWorkflowTest {

    @Test
    void shouldCreateLeaseSuccessfully() {

        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusMonths(12);

        Lease lease = Lease.create(
                tenantId,
                propertyId,
                unitId,
                tenantProfileId,
                "LS-2026-001",
                LeaseType.STANDARD,
                BillingCycle.MONTHLY,
                startDate,
                endDate,
                new BigDecimal("25000"),
                new BigDecimal("50000"),
                new BigDecimal("1500"),
                7,
                false
        );

        assertNotNull(lease);

        assertEquals(tenantId, lease.getTenantId());
        assertEquals(propertyId, lease.getPropertyId());
        assertEquals(unitId, lease.getUnitId());
        assertEquals(tenantProfileId, lease.getTenantProfileId());

        assertEquals(LeaseStatus.DRAFT, lease.getStatus());

        assertEquals(new BigDecimal("25000"), lease.getRentAmount());
        assertEquals(new BigDecimal("50000"), lease.getSecurityDeposit());
        assertEquals(new BigDecimal("1500"), lease.getLateFeeAmount());

        assertEquals(7, lease.getGracePeriodDays());
        assertFalse(lease.isAutoRenew());

        assertEquals(startDate, lease.getStartDate());
        assertEquals(endDate, lease.getEndDate());
    }
}