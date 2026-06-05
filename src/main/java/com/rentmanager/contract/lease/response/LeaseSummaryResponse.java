package com.rentmanager.contract.lease.response;

import com.rentmanager.contract.lease.dto.LeaseStatusDTO;

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