package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.api.controller.RentLedgerCommandControllerKeyValidationProbe;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * V98 idempotency for manually recorded rent transactions. Manual mocks, no
 * MockitoExtension — see CLAUDE.md test conventions.
 */
class RentLedgerIdempotencyTest {

    private RentLedgerEntryRepository entryRepository;
    private RentTransactionRepository transactionRepository;
    private RentLedgerApplicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        entryRepository = mock(RentLedgerEntryRepository.class);
        transactionRepository = mock(RentTransactionRepository.class);
        service = new RentLedgerApplicationService(
                entryRepository, transactionRepository,
                mock(LeaseRepository.class), mock(TenantRepository.class),
                mock(DepositCommandService.class), mock(DomainEventPublisher.class));
    }

    private RentLedgerEntry entry() {
        return RentLedgerEntry.create(
                tenantId, "corr", leaseId, UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1),
                new BigDecimal("15000.00"), false);
    }

    @Test
    void repeatedKeyForSameEntryWritesNothing() {
        RentLedgerEntry entry = entry();
        RentTransaction earlier = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("5000"), null, RentTransactionSource.CASH, "staff@x", LocalDateTime.now()
        ).withIdempotencyKey("key-000000001");
        when(transactionRepository.findByIdempotencyKey(tenantId, "key-000000001")).thenReturn(Optional.of(earlier));
        when(entryRepository.findByIdAndTenantId(entry.getId(), tenantId)).thenReturn(Optional.of(entry));

        RentLedgerEntry result = service.applyTransaction(
                tenantId, "corr-2", entry.getId(), RentTransactionType.PAYMENT, new BigDecimal("5000"),
                null, RentTransactionSource.CASH, "staff@x", LocalDateTime.now(), "key-000000001");

        assertThat(result).isSameAs(entry);
        verify(transactionRepository, never()).save(any());
        verify(entryRepository, never()).save(any());
    }

    @Test
    void keyReusedForDifferentEntryIsRefused() {
        RentTransaction earlier = RentTransaction.create(
                tenantId, UUID.randomUUID(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("5000"), null, RentTransactionSource.CASH, "staff@x", LocalDateTime.now()
        ).withIdempotencyKey("key-000000002");
        when(transactionRepository.findByIdempotencyKey(tenantId, "key-000000002")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.applyTransaction(
                tenantId, "corr", UUID.randomUUID(), RentTransactionType.PAYMENT, new BigDecimal("5000"),
                null, RentTransactionSource.CASH, "staff@x", LocalDateTime.now(), "key-000000002"))
                .isInstanceOf(RentLedgerStateException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void newKeyRecordsTheTransactionCarryingTheKey() {
        RentLedgerEntry entry = entry();
        when(transactionRepository.findByIdempotencyKey(tenantId, "key-000000003")).thenReturn(Optional.empty());
        when(entryRepository.findByIdAndTenantId(entry.getId(), tenantId)).thenReturn(Optional.of(entry));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.applyTransaction(
                tenantId, "corr", entry.getId(), RentTransactionType.PAYMENT, new BigDecimal("5000"),
                null, RentTransactionSource.CASH, "staff@x", LocalDateTime.now(), "key-000000003");

        verify(transactionRepository).save(argThat(t -> "key-000000003".equals(t.getIdempotencyKey())));
    }

    @Test
    void headerValidationAcceptsSaneKeysAndRejectsOthers() {
        assertThatCode(() -> RentLedgerCommandControllerKeyValidationProbe.validate(null)).doesNotThrowAnyException();
        assertThatCode(() -> RentLedgerCommandControllerKeyValidationProbe.validate("0b8f5f7e-2d1c-4a8b-9f7e-1a2b3c4d5e6f"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> RentLedgerCommandControllerKeyValidationProbe.validate("short"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RentLedgerCommandControllerKeyValidationProbe.validate("has spaces in it"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> RentLedgerCommandControllerKeyValidationProbe.validate("x".repeat(101)))
                .isInstanceOf(BusinessException.class);
    }
}
