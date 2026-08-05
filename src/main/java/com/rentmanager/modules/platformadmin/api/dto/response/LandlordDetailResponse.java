package com.rentmanager.modules.platformadmin.api.dto.response;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Full landlord view for {@code GET /api/v1/admin/landlords/{id}}: org
 * profile, aggregated money, effective commission (with source), plus the
 * four read-only collections the operations team needs day to day.
 *
 * {@code lastActivityAt} and {@code RentTransactionSummary.occurredAt} are
 * {@code LocalDateTime} because they derive from the rent-transaction
 * {@code occurred_at} column (a {@code LocalDateTime}), while
 * {@code createdAt} values derive from {@code BaseEntity} timestamps
 * ({@code Instant}).
 */
public record LandlordDetailResponse(
        UUID id,
        String name,
        String slug,
        String email,
        String phoneNumber,
        TenantStatus status,
        BillingMode billingMode,
        Instant createdAt,
        LocalDateTime lastActivityAt,
        BigDecimal gmvAmount,
        BigDecimal commissionAmount,
        BigDecimal effectiveCommissionRate,
        String commissionSource,
        List<PropertySummary> properties,
        List<RenterSummary> renters,
        List<LeaseSummary> leases,
        List<PaymentRequestSummary> paymentRequests,
        List<DisbursementSummary> disbursements,
        List<RentTransactionSummary> recentTransactions
) {

    public record PropertySummary(
            UUID id,
            String referenceCode,
            String name,
            PropertyStatus status,
            PropertyType propertyType,
            PremisesType premisesType,
            long unitsCount,
            long occupiedUnitsCount
    ) {}

    public record RenterSummary(
            UUID id,
            String fullName,
            String email,
            String phone,
            String nationalId
    ) {}

    public record LeaseSummary(
            UUID id,
            String leaseNumber,
            LeaseStatus status,
            UUID propertyId,
            UUID unitId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal rentAmount
    ) {}

    public record PaymentRequestSummary(
            UUID id,
            BigDecimal amount,
            RentPaymentRequestStatus status,
            String mpesaReceiptNumber,
            Instant createdAt
    ) {}

    public record DisbursementSummary(
            UUID id,
            BigDecimal amount,
            String recipientName,
            DisbursementStatus status,
            boolean requiresManualAttention,
            Instant createdAt
    ) {}

    public record RentTransactionSummary(
            UUID id,
            BigDecimal amount,
            BigDecimal commissionAmount,
            RentTransactionSource source,
            LocalDateTime occurredAt
    ) {}
}