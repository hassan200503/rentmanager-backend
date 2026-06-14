package com.rentmanager.modules.lease.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseProjectionDTO(
        UUID id,
        String leaseNumber,
        LeaseStatusDTO status,
        BigDecimal rentAmount,
        LocalDate startDate,
        LocalDate endDate
) {}