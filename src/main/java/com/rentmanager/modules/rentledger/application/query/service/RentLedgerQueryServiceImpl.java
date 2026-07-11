package com.rentmanager.modules.rentledger.application.query.service;

import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerEntryNotFoundException;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return rentLedgerEntryRepository.findByTenantAndStatus(tenantId, status)
                .stream()
                .map(RentLedgerEntryResponse::from)
                .toList();
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
}