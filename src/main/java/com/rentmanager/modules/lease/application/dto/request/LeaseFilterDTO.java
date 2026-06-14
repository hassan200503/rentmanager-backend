package com.rentmanager.modules.lease.application.dto.request;

import java.time.LocalDate;
import java.util.UUID;

public record LeaseFilterDTO(
        UUID tenantId,
        UUID propertyId,
        LeaseStatusDTO status,
        LocalDate fromDate,
        LocalDate toDate
) {}