package com.rentmanager.modules.lease.application.command.validator;

import com.rentmanager.modules.lease.application.command.CreateLeaseCommand;
import com.rentmanager.modules.lease.application.port.out.PropertyQueryPort;
import com.rentmanager.modules.lease.application.port.out.TenantProfileQueryPort;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.LeaseValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Application-level guard. Runs BEFORE the domain aggregate is touched.
 *
 * Responsibilities:
 *  - Structural checks (nulls, blanks, ranges) that the HTTP layer may have missed
 *  - Cross-aggregate checks (does the property exist? is the unit already leased?)
 *  - Business rules that require infrastructure (DB queries)
 *
 * What it does NOT do:
 *  - It does not enforce domain invariants — Lease.create() owns those
 *  - It does not persist anything
 *  - It does not know about HTTP or events
 */
@Component
@RequiredArgsConstructor
public class CreateLeaseValidator {

    private final LeaseRepository leaseRepository;
    private final PropertyQueryPort propertyQueryPort;
    private final TenantProfileQueryPort tenantProfileQueryPort;

    public void validate(CreateLeaseCommand cmd) {

        // --- Structural guards ---
        requireNonNull(cmd.tenantId(), ErrorCode.LEASE_TENANT_NULL, "tenantId is required");
        requireNonNull(cmd.propertyId(), ErrorCode.LEASE_PROPERTY_NULL, "propertyId is required");
        requireNonNull(cmd.unitId(), ErrorCode.LEASE_UNIT_NULL, "unitId is required");
        requireNonNull(cmd.tenantProfileId(), ErrorCode.LEASE_PROFILE_NULL, "tenantProfileId is required");
        requireNonNull(cmd.leaseType(), ErrorCode.LEASE_TYPE_REQUIRED, "leaseType is required");
        requireNonNull(cmd.billingCycle(), ErrorCode.LEASE_BILLING_CYCLE_REQUIRED, "billingCycle is required");

        validateDateRange(cmd.startDate(), cmd.endDate());
        validateRent(cmd.monthlyRent());
        validateDeposit(cmd.securityDeposit());

        // --- Cross-aggregate / infrastructure checks ---
        if (!propertyQueryPort.existsById(cmd.propertyId())) {
            throw new LeaseValidationException(
                    "Property not found: " + cmd.propertyId(),
                    ErrorCode.LEASE_PROPERTY_NOT_FOUND
            );
        }

        if (!tenantProfileQueryPort.existsById(cmd.tenantProfileId())) {
            throw new LeaseValidationException(
                    "Tenant profile not found: " + cmd.tenantProfileId(),
                    ErrorCode.LEASE_PROFILE_NOT_FOUND
            );
        }

        if (leaseRepository.hasActiveLeaseForUnit(cmd.unitId())) {
            throw new LeaseValidationException(
                    "Unit already has an active lease: " + cmd.unitId(),
                    ErrorCode.LEASE_UNIT_ALREADY_LEASED
            );
        }
    }

    // --- Private helpers ---

    private void requireNonNull(Object value, ErrorCode code, String message) {
        if (value == null) {
            throw new LeaseValidationException(message, code);
        }
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new LeaseValidationException(
                    "Start and end dates are required",
                    ErrorCode.LEASE_INVALID_DATE_RANGE
            );
        }
        if (!end.isAfter(start)) {
            throw new LeaseValidationException(
                    "End date must be after start date",
                    ErrorCode.LEASE_INVALID_DATE_RANGE
            );
        }
        if (start.isBefore(LocalDate.now())) {
            throw new LeaseValidationException(
                    "Start date cannot be in the past",
                    ErrorCode.LEASE_START_DATE_IN_PAST
            );
        }
    }

    private void validateRent(BigDecimal rent) {
        if (rent == null || rent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new LeaseValidationException(
                    "Monthly rent must be greater than zero",
                    ErrorCode.LEASE_INVALID_RENT
            );
        }
    }

    private void validateDeposit(BigDecimal deposit) {
        if (deposit == null || deposit.compareTo(BigDecimal.ZERO) < 0) {
            throw new LeaseValidationException(
                    "Security deposit must be >= 0",
                    ErrorCode.LEASE_INVALID_DEPOSIT
            );
        }
    }
}