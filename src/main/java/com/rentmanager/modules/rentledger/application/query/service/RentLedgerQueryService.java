package com.rentmanager.modules.rentledger.application.query.service;

import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;

import java.util.List;
import java.util.UUID;

public interface RentLedgerQueryService {

    RentLedgerEntryResponse getById(UUID tenantId, UUID entryId);

    List<RentLedgerEntryResponse> getByLease(UUID tenantId, UUID leaseId);

    List<RentLedgerEntryResponse> getByStatus(UUID tenantId, RentLedgerStatus status);

    List<RentTransactionResponse> getTransactionsForEntry(UUID tenantId, UUID entryId);
}