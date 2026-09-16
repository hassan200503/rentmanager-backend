package com.rentmanager.modules.lease.application.dto.request;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.util.UUID;

public record LeaseSearchRequest(

        UUID tenantId,

        UUID propertyId,

        LeaseStatusDTO status,

        /** Matches lease number, tenant full name, or tenant phone — see LeaseApplicationService#search. */
        String keyword,

        LocalDate fromDate,

        LocalDate toDate,

        @Min(value = 0, message = "page must be >= 0")
        int page,

        @Min(value = 1, message = "size must be >= 1")
        @Max(value = 100, message = "size must be <= 100")
        int size
) {}