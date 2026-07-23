package com.rentmanager.modules.rentledger.application.query.service;

import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerEntryNotFoundException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Mockito-only unit tests for the query-service layer — mirrors
 * RentLedgerApplicationServiceTest's convention (no DB, repositories
 * mocked), applied here to the read side rather than the write side.
 *
 * getTransactionsForEntry (added this session): the two behaviors worth
 * locking in are (1) a missing entryId throws
 * RentLedgerEntryNotFoundException BEFORE the transaction repository is
 * ever queried — not caught downstream as an empty list — and (2) the
 * transaction repository is queried with the same tenantId/entryId that
 * were validated against the entry repository, not silently substituted.
 */
@ExtendWith(MockitoExtension.class)
class RentLedgerQueryServiceImplTest {

    @Mock
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Mock
    private RentTransactionRepository rentTransactionRepository;
    @Mock
    private LeaseRepository leaseRepository;
    @Mock
    private TenantProfileRepository tenantProfileRepository;

    private RentLedgerQueryServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RentLedgerQueryServiceImpl(rentLedgerEntryRepository, rentTransactionRepository, leaseRepository, tenantProfileRepository);
    }

    private RentLedgerEntry newEntry() {
        return RentLedgerEntry.create(
                tenantId, "corr", leaseId, unitId, tenantProfileId,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1),
                new BigDecimal("1000.00"), false
        );
    }

    @Nested
    class GetById {

        @Test
        void returnsMappedResponseWhenFound() {
            RentLedgerEntry entry = newEntry();
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));

            RentLedgerEntryResponse response = service.getById(tenantId, entry.getId());

            assertThat(response.id()).isEqualTo(entry.getId());
            assertThat(response.status()).isEqualTo(RentLedgerStatus.DUE.name());
        }

        @Test
        void throwsNotFoundWhenMissing() {
            UUID missingId = UUID.randomUUID();
            when(rentLedgerEntryRepository.findByIdAndTenantId(missingId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(tenantId, missingId))
                    .isInstanceOf(RentLedgerEntryNotFoundException.class);
        }
    }

    @Nested
    class GetTransactionsForEntry {

        @Test
        void throwsNotFoundWhenEntryMissingAndNeverQueriesTransactions() {
            UUID missingId = UUID.randomUUID();
            when(rentLedgerEntryRepository.findByIdAndTenantId(missingId, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getTransactionsForEntry(tenantId, missingId))
                    .isInstanceOf(RentLedgerEntryNotFoundException.class);

            verifyNoInteractions(rentTransactionRepository);
        }

        @Test
        void returnsEmptyListWhenEntryExistsButHasNoTransactions() {
            RentLedgerEntry entry = newEntry();
            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));
            when(rentTransactionRepository.findByLedgerEntry(tenantId, entry.getId()))
                    .thenReturn(List.of());

            List<RentTransactionResponse> result = service.getTransactionsForEntry(tenantId, entry.getId());

            assertThat(result).isEmpty();
        }

        @Test
        void returnsMappedTransactionsInRepositoryOrder() {
            RentLedgerEntry entry = newEntry();
            RentTransaction charge = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.RENT_CHARGE,
                    new BigDecimal("1000.00"), null, RentTransactionSource.SYSTEM,
                    "SYSTEM", LocalDateTime.now().minusDays(1)
            );
            RentTransaction payment = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("400.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            when(rentLedgerEntryRepository.findByIdAndTenantId(entry.getId(), tenantId))
                    .thenReturn(Optional.of(entry));
            when(rentTransactionRepository.findByLedgerEntry(tenantId, entry.getId()))
                    .thenReturn(List.of(charge, payment));

            List<RentTransactionResponse> result = service.getTransactionsForEntry(tenantId, entry.getId());

            assertThat(result).hasSize(2);
            assertThat(result.get(0).type()).isEqualTo(RentTransactionType.RENT_CHARGE.name());
            assertThat(result.get(1).type()).isEqualTo(RentTransactionType.PAYMENT.name());
            assertThat(result.get(1).externalReference()).isEqualTo("MPESA-1");
        }
    }
}