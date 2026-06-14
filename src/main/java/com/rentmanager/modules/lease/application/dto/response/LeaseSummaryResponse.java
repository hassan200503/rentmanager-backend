package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LeaseSummaryResponse(
        UUID id,
        String leaseNumber,
        LeaseStatusDTO status,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal rentAmount
) {}