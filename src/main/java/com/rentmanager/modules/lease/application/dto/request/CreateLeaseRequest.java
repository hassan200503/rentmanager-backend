package com.rentmanager.modules.lease.application.dto.request;

import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLeaseRequest(


        @NotNull(message = "propertyId is required")
        UUID propertyId,

        @NotNull(message = "unitId is required")
        UUID unitId,

        @NotNull(message = "tenantProfileId is required")
        UUID tenantProfileId,

        @NotBlank(message = "leaseNumber is required")
        String leaseNumber,

        @NotNull(message = "leaseType is required")
        LeaseTypeDTO leaseType,

        @NotNull(message = "billingCycle is required")
        BillingCycleDTO billingCycle,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotNull(message = "rentAmount is required")
        @Positive(message = "rentAmount must be > 0")
        BigDecimal rentAmount,

        @NotNull(message = "securityDeposit is required")
        @PositiveOrZero(message = "securityDeposit must be >= 0")
        BigDecimal securityDeposit,

        @NotNull(message = "lateFeeAmount is required")
        @PositiveOrZero(message = "lateFeeAmount must be >= 0")
        BigDecimal lateFeeAmount,

        @NotNull(message = "gracePeriodDays is required")
        @Min(value = 0, message = "gracePeriodDays cannot be negative")
        Integer gracePeriodDays,

        @NotNull(message = "autoRenew is required")
        Boolean autoRenew
) {}