package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TenantLeaseResponse(
        UUID leaseId,
        String leaseNumber,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        String status,
        String unitNumber,
        String unitLabel,
        String propertyName,
        String propertyAddress,
        String landlordName,
        String landlordPhone,
        String landlordEmail,
        String terms
) {}
