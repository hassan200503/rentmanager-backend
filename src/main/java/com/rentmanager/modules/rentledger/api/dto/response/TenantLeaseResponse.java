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
        String landlordCode,
        String landlordAddress,
        String landlordLogoUrl,
        String landlordSince,
        boolean landlordVerified,
        String terms,
        String managerName,
        String managerPhone,
        String managerEmail,
        String emergencyContactPhone,
        boolean emergencyContact24h,
        String landlordPrimaryColor,
        String landlordSecondaryColor,
        String billingMode,
        String subscriptionStatus
) {}
