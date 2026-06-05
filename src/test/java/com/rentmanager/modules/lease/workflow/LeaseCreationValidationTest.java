package com.rentmanager.modules.lease.workflow;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.shared.exception.LeaseStateException;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class LeaseCreationValidationTest {

    @Test
    void shouldThrowExceptionWhenTenantIsNull() {

        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusMonths(12);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> Lease.create(
                        null,
                        propertyId,
                        unitId,
                        tenantProfileId,
                        "LS-001",
                        LeaseType.MONTH_TO_MONTH,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        new BigDecimal("20000"),
                        new BigDecimal("30000"),
                        new BigDecimal("1000"),
                        7,
                        false
                )
        );

        assertEquals(ErrorCode.LEASE_TENANT_NULL, ex.getErrorCode());
    }

    @Test
    void shouldRejectEndDateBeforeStartDate() {

        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(10);
        LocalDate endDate = LocalDate.now().plusDays(1);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> Lease.create(
                        tenantId,
                        propertyId,
                        unitId,
                        tenantProfileId,
                        "LS-002",
                        LeaseType.MONTH_TO_MONTH,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        new BigDecimal("20000"),
                        new BigDecimal("30000"),
                        new BigDecimal("1000"),
                        7,
                        false
                )
        );

        assertEquals(ErrorCode.LEASE_INVALID_DATE_RANGE, ex.getErrorCode());
    }

    @Test
    void shouldRejectNegativeMonthlyRent() {

        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusMonths(12);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> Lease.create(
                        tenantId,
                        propertyId,
                        unitId,
                        tenantProfileId,
                        "LS-003",
                        LeaseType.MONTH_TO_MONTH,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        new BigDecimal("-1000"),
                        new BigDecimal("30000"),
                        new BigDecimal("1000"),
                        7,
                        false
                )
        );

        assertEquals(ErrorCode.LEASE_INVALID_RENT, ex.getErrorCode());
    }

    @Test
    void shouldRejectBlankLeaseNumber() {

        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusMonths(12);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> Lease.create(
                        tenantId,
                        propertyId,
                        unitId,
                        tenantProfileId,
                        "   ",
                        LeaseType.MONTH_TO_MONTH,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        new BigDecimal("20000"),
                        new BigDecimal("30000"),
                        new BigDecimal("1000"),
                        7,
                        false
                )
        );

        assertEquals(ErrorCode.LEASE_NUMBER_BLANK, ex.getErrorCode());
    }

    @Test
    void shouldRejectNegativeSecurityDeposit() {

        UUID tenantId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID tenantProfileId = UUID.randomUUID();

        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusMonths(12);

        LeaseStateException ex = assertThrows(
                LeaseStateException.class,
                () -> Lease.create(
                        tenantId,
                        propertyId,
                        unitId,
                        tenantProfileId,
                        "LS-004",
                        LeaseType.MONTH_TO_MONTH,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        new BigDecimal("20000"),
                        new BigDecimal("-500"),
                        new BigDecimal("1000"),
                        7,
                        false
                )
        );

        assertEquals(ErrorCode.LEASE_INVALID_DEPOSIT, ex.getErrorCode());
    }
}