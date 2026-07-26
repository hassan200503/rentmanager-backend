package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse.PaymentHistoryItem;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentReceiptResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentSummaryResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPortalService {

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;

    @Transactional(readOnly = true)
    public TenantDashboardResponse getDashboard(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));
        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, activeLease.getId());

        BigDecimal currentBalance = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = entries.stream()
                .filter(e -> e.getStatus() == RentLedgerStatus.OVERDUE)
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RentLedgerEntry nextDueEntry = entries.stream()
                .filter(e -> e.getDueDate() != null && !e.getDueDate().isBefore(LocalDate.now()))
                .filter(e -> e.getStatus() != RentLedgerStatus.PAID && e.getStatus() != RentLedgerStatus.OVERPAID)
                .min(Comparator.comparing(RentLedgerEntry::getDueDate))
                .orElse(null);

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());
        List<RentTransaction> recentPayments = allTxns.stream()
                .filter(t -> t.reducesBalanceOwed() || t.getType() == RentTransactionType.PAYMENT)
                .sorted(Comparator.comparing(RentTransaction::getOccurredAt).reversed())
                .limit(5)
                .toList();

        return new TenantDashboardResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                currentBalance,
                nextDueEntry != null ? nextDueEntry.getDueDate() : null,
                nextDueEntry != null ? nextDueEntry.getAmountDue() : BigDecimal.ZERO,
                overdueAmount,
                activeLease.getStatus().name(),
                unit.getUnitNumber(),
                property.getName(),
                activeLease.getRentAmount(),
                activeLease.getSecurityDeposit() != null ? activeLease.getSecurityDeposit() : BigDecimal.ZERO,
                recentPayments.stream().map(this::toPaymentHistoryItem).toList()
        );
    }

    @Transactional(readOnly = true)
    public TenantLeaseResponse getLease(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));
        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));
        Tenant landlord = tenantRepository.findById(landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        String address = "";
        if (property.getAddress() != null) {
            address = property.getAddress().toString();
        }

        return new TenantLeaseResponse(
                activeLease.getId(),
                activeLease.getLeaseNumber(),
                activeLease.getStartDate(),
                activeLease.getEndDate(),
                activeLease.getRentAmount(),
                activeLease.getSecurityDeposit() != null ? activeLease.getSecurityDeposit() : BigDecimal.ZERO,
                activeLease.getStatus().name(),
                unit.getUnitNumber(),
                unit.getLabel(),
                property.getName(),
                address,
                landlord.getName(),
                landlord.getPhoneNumber(),
                landlord.getEmail(),
                ""
        );
    }

    @Transactional(readOnly = true)
    public TenantPaymentSummaryResponse getPaymentSummary(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());

        BigDecimal totalPaid = allTxns.stream()
                .filter(t -> t.reducesBalanceOwed())
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDue = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.RENT_CHARGE)
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, activeLease.getId());
        BigDecimal currentBalance = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = entries.stream()
                .filter(e -> e.getStatus() == RentLedgerStatus.OVERDUE)
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int currentYear = LocalDate.now().getYear();
        long paymentsThisYear = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.PAYMENT)
                .filter(t -> t.getOccurredAt() != null && t.getOccurredAt().getYear() == currentYear)
                .count();

        RentTransaction lastPayment = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.PAYMENT)
                .max(Comparator.comparing(RentTransaction::getOccurredAt))
                .orElse(null);

        return new TenantPaymentSummaryResponse(
                totalPaid,
                totalDue,
                currentBalance,
                overdueAmount,
                (int) paymentsThisYear,
                lastPayment != null ? lastPayment.getOccurredAt() : null,
                lastPayment != null ? lastPayment.getAmount() : BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public TenantPaymentHistoryResponse getPaymentHistory(UUID userId, int page, int size) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());
        List<RentTransaction> sorted = allTxns.stream()
                .sorted(Comparator.comparing(RentTransaction::getOccurredAt).reversed())
                .toList();

        int totalElements = sorted.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<PaymentHistoryItem> pageContent;
        if (fromIndex >= totalElements) {
            pageContent = List.of();
        } else {
            pageContent = sorted.subList(fromIndex, toIndex).stream()
                    .map(this::toPaymentHistoryItem)
                    .toList();
        }

        return new TenantPaymentHistoryResponse(
                pageContent,
                totalElements,
                totalPages,
                page,
                size,
                page == 0,
                page >= totalPages - 1,
                pageContent.isEmpty()
        );
    }

    @Transactional(readOnly = true)
    public TenantPaymentReceiptResponse getPaymentReceipt(UUID userId, UUID transactionId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();

        RentTransaction txn = rentTransactionRepository.findByIdAndTenantId(transactionId, landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Transaction not found", ErrorCode.RESOURCE_NOT_FOUND));

        Lease lease = leaseRepository.findByIdAndTenantId(txn.getLeaseId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Lease not found", ErrorCode.RESOURCE_NOT_FOUND));

        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));

        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, lease.getId());
        BigDecimal balanceAfter = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String receiptNumber = "RCP-" + txn.getId().toString().substring(0, 8).toUpperCase();
        String billingPeriod = "";
        if (!entries.isEmpty()) {
            billingPeriod = entries.get(0).getBillingPeriodStart().toString();
        }

        return new TenantPaymentReceiptResponse(
                txn.getId(),
                receiptNumber,
                txn.getOccurredAt(),
                txn.getAmount(),
                txn.getExternalReference(),
                profile.getFullName(),
                profile.getPhone(),
                unit.getUnitNumber(),
                property.getName(),
                billingPeriod,
                billingPeriod,
                balanceAfter,
                null,
                null
        );
    }

    private TenantProfile resolveTenantProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RentLedgerStateException("User not found", ErrorCode.RESOURCE_NOT_FOUND));
        String clerkUserId = user.getClerkUserId();
        return tenantProfileRepository.findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new RentLedgerStateException("Tenant profile not found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Lease findActiveLease(UUID landlordTenantId, UUID tenantProfileId) {
        List<Lease> allLeases = leaseRepository.findAllByTenant(landlordTenantId);
        return allLeases.stream()
                .filter(l -> l.getTenantProfileId().equals(tenantProfileId))
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new RentLedgerStateException("No active lease found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    private PaymentHistoryItem toPaymentHistoryItem(RentTransaction txn) {
        String mpesaRef = txn.getSource() == RentTransactionSource.MPESA ? txn.getExternalReference() : null;
        return new PaymentHistoryItem(
                txn.getId(),
                txn.getType().name(),
                txn.getAmount(),
                txn.getSource().name(),
                txn.getExternalReference(),
                txn.getOccurredAt() != null ? txn.getOccurredAt().toString() : "",
                txn.getType().name(),
                "",
                "",
                mpesaRef
        );
    }
}
