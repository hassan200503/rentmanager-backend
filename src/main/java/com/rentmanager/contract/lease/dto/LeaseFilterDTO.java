package com.rentmanager.contract.lease.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LeaseFilterDTO(
        UUID tenantId,
        UUID propertyId,
        LeaseStatusDTO status,
        LocalDate fromDate,
        LocalDate toDate
) {}