package com.rentmanager.modules.rentledger.application.query.service;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.api.dto.response.LeaseBalanceSummaryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionSummaryResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerEntryNotFoundException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * NOT_FOUND handling note: rent-ledger's ErrorCode section has no dedicated
 * not-found code (unlike PROPERTY_NOT_FOUND / LEASE_NOT_FOUND / DEPOSIT_NOT_FOUND,
 * each of which has one). Using the generic ErrorCode.RESOURCE_NOT_FOUND with
 * RentLedgerStateException — which extends BusinessException and is mapped to
 * 400, not 404, by GlobalExceptionHandler — rather than PropertyNotFoundException's
 * 404 pattern, since introducing a second not-found exception type into this
 * module (alongside RentLedgerStateException, already used for the lease-missing
 * case in postCharge) seemed like a worse inconsistency than the cross-module
 * 400-vs-404 mismatch with Property. Flagged for confirmation — add a dedicated
 * RENT_LEDGER_ENTRY_NOT_FOUND code + 404 handler as a follow-up if you'd rather
 * match Property's convention exactly.
 *
 * getTransactionsForEntry (added this session): verifies the ledger entry
 * exists for this tenant before listing its transactions, reusing
 * getById's existing not-found handling, rather than letting a bad
 * entryId silently return an empty list — an empty list must mean "entry
 * exists, no transactions", not "entry doesn't exist", for the frontend
 * drill-down to render a correct empty state vs. a 404.
 */
@Service
@RequiredArgsConstructor
public class RentLedgerQueryServiceImpl implements RentLedgerQueryService {

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;
    private final LeaseRepository leaseRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

    /**
     * Precedence when one lease has entries in more than one of these
     * statuses across different billing periods (e.g. last month PAID,
     * this month OVERDUE, and an old OVERPAID nobody resolved). OVERDUE is
     * the clearest collections problem and always surfaces first; OVERPAID
     * is a distinct admin-action item rather than a debt, so it's next;
     * PARTIALLY_PAID is closer to becoming a problem than plain DUE.
     */
    private static final List<RentLedgerStatus> STATUS_PRIORITY = List.of(
            RentLedgerStatus.OVERDUE,
            RentLedgerStatus.OVERPAID,
            RentLedgerStatus.PARTIALLY_PAID,
            RentLedgerStatus.DUE
    );

    @Override
    public RentLedgerEntryResponse getById(UUID tenantId, UUID entryId) {
        return rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)
                .map(RentLedgerEntryResponse::from)
                .orElseThrow(() -> new RentLedgerEntryNotFoundException(
                        entryId,
                        "Rent ledger entry not found: " + entryId
                ));
    }

    @Override
    public List<RentLedgerEntryResponse> getByLease(UUID tenantId, UUID leaseId) {
        return rentLedgerEntryRepository.findByLease(tenantId, leaseId)
                .stream()
                .map(RentLedgerEntryResponse::from)
                .toList();
    }

    @Override
    public List<RentLedgerEntryResponse> getByStatus(UUID tenantId, RentLedgerStatus status) {
        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByTenantAndStatus(tenantId, status);
        if (entries.isEmpty()) return List.of();

        // Batch-load leases, units, properties and profiles in 4 queries rather
        // than N, so this endpoint stays O(1) queries regardless of portfolio size.
        Set<UUID> leaseIds = entries.stream().map(RentLedgerEntry::getLeaseId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Lease> leaseMap = leaseRepository.findAllByIdIn(leaseIds).stream()
                .collect(Collectors.toMap(Lease::getId, l -> l));

        Set<UUID> unitIds = entries.stream().map(RentLedgerEntry::getUnitId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Unit> unitMap = unitIds.isEmpty() ? Collections.emptyMap()
                : unitRepository.findAllByTenantIdAndIdIn(tenantId, new ArrayList<>(unitIds)).stream()
                        .collect(Collectors.toMap(Unit::getId, u -> u));

        Set<UUID> propertyIds = unitMap.values().stream().map(Unit::getPropertyId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Property> propertyMap = propertyIds.isEmpty() ? Collections.emptyMap()
                : propertyRepository.findAllByTenantIdAndIdIn(tenantId, new ArrayList<>(propertyIds)).stream()
                        .collect(Collectors.toMap(Property::getId, p -> p));

        Set<UUID> profileIds = entries.stream().map(RentLedgerEntry::getTenantProfileId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, TenantProfile> profileMap = profileIds.isEmpty() ? Collections.emptyMap()
                : tenantProfileRepository.findAllById(profileIds).stream()
                        .collect(Collectors.toMap(TenantProfile::getId, p -> p));

        return entries.stream().map(entry -> {
            Lease lease = leaseMap.get(entry.getLeaseId());
            Unit unit = unitMap.get(entry.getUnitId());
            Property property = unit != null ? propertyMap.get(unit.getPropertyId()) : null;
            TenantProfile profile = profileMap.get(entry.getTenantProfileId());
            return new RentLedgerEntryResponse(
                    entry.getId(),
                    entry.getLeaseId(),
                    entry.getUnitId(),
                    entry.getTenantProfileId(),
                    entry.getBillingPeriodStart(),
                    entry.getBillingPeriodEnd(),
                    entry.getDueDate(),
                    entry.getAmountDue(),
                    entry.getAmountPaid(),
                    entry.getBalanceOwed(),
                    entry.getExcessAmount(),
                    entry.getStatus().name(),
                    entry.isProrated(),
                    entry.getVersion(),
                    profile != null ? profile.getFullName() : null,
                    unit != null ? unit.getUnitNumber() : null,
                    property != null ? property.getName() : null,
                    lease != null ? lease.getLeaseNumber() : null
            );
        }).toList();
    }

    @Override
    public List<RentTransactionResponse> getTransactionsForEntry(UUID tenantId, UUID entryId) {
        rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)
                .orElseThrow(() -> new RentLedgerEntryNotFoundException(
                        entryId,
                        "Rent ledger entry not found: " + entryId
                ));

        return rentTransactionRepository.findByLedgerEntry(tenantId, entryId)
                .stream()
                .map(RentTransactionResponse::from)
                .toList();
    }

    @Override
    public List<RentTransactionSummaryResponse> getAllTransactions(UUID tenantId) {
        List<com.rentmanager.modules.rentledger.domain.model.RentTransaction> transactions =
                rentTransactionRepository.findAllByTenant(tenantId);

        if (transactions.isEmpty()) return List.of();

        Set<UUID> leaseIds = transactions.stream()
                .map(com.rentmanager.modules.rentledger.domain.model.RentTransaction::getLeaseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Lease> leaseMap = leaseRepository.findAllByIdIn(leaseIds).stream()
                .collect(Collectors.toMap(Lease::getId, l -> l));

        Set<UUID> profileIds = leaseMap.values().stream()
                .map(Lease::getTenantProfileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, TenantProfile> profileMap = profileIds.isEmpty()
                ? Collections.emptyMap()
                : tenantProfileRepository.findAllById(profileIds).stream()
                        .collect(Collectors.toMap(TenantProfile::getId, p -> p));

        return transactions.stream()
                .map(tx -> {
                    Lease lease = leaseMap.get(tx.getLeaseId());
                    TenantProfile profile = lease != null ? profileMap.get(lease.getTenantProfileId()) : null;
                    return new RentTransactionSummaryResponse(
                            tx.getId(),
                            tx.getLedgerEntryId(),
                            tx.getLeaseId(),
                            tx.getType().name(),
                            tx.getAmount(),
                            tx.getExternalReference(),
                            tx.getSource().name(),
                            tx.getRecordedBy(),
                            tx.getOccurredAt(),
                            profile != null ? profile.getFullName() : null,
                            profile != null ? profile.getPhone() : null,
                            lease != null ? lease.getLeaseNumber() : null,
                            lease != null ? lease.getStatus().name() : null
                    );
                })
                .toList();
    }

    @Override
    public List<LeaseBalanceSummaryResponse> getBalanceByLease(UUID tenantId) {
        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByTenantAndStatusIn(
                tenantId, STATUS_PRIORITY
        );

        Map<UUID, List<RentLedgerEntry>> byLease = entries.stream()
                .collect(Collectors.groupingBy(RentLedgerEntry::getLeaseId));

        return byLease.entrySet().stream()
                .map(e -> {
                    List<RentLedgerEntry> leaseEntries = e.getValue();

                    BigDecimal outstanding = leaseEntries.stream()
                            .map(RentLedgerEntry::getBalanceOwed)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    RentLedgerStatus worst = leaseEntries.stream()
                            .map(RentLedgerEntry::getStatus)
                            .min(Comparator.comparingInt(STATUS_PRIORITY::indexOf))
                            .orElseThrow();

                    LocalDate oldestUnpaidDueDate = leaseEntries.stream()
                            .map(RentLedgerEntry::getDueDate)
                            .min(Comparator.naturalOrder())
                            .orElse(null);

                    return new LeaseBalanceSummaryResponse(
                            e.getKey(), outstanding, worst.name(), oldestUnpaidDueDate
                    );
                })
                .toList();
    }
}