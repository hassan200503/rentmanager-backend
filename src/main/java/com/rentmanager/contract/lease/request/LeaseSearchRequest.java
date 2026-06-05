package com.rentmanager.contract.lease.request;

import com.rentmanager.contract.lease.dto.LeaseStatusDTO;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public record LeaseSearchRequest(

        UUID tenantId,

        UUID propertyId,

        LeaseStatusDTO status,

        LocalDate fromDate,

        LocalDate toDate,

        @Min(value = 0, message = "page must be >= 0")
        int page,

        @Min(value = 1, message = "size must be >= 1")
        @Max(value = 100, message = "size must be <= 100")
        int size
) {}