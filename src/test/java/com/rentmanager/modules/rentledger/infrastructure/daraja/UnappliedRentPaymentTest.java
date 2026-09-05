package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.integration.bridge.PlatformDarajaCredentialsResolver;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the money-loss path found by running a real payment on 2026-09-05.
 *
 * <p>What happened: an STK push was prompted against a rent ledger entry that
 * was already PAID. Safaricom took the renter's money and returned
 * {@code ResultCode=0}. The callback then threw
 * {@code cannot modify a PAID entry} while applying it, the exception escaped
 * the callback handler, and the result was the worst combination available —
 * the payment recorded nowhere, Safaricom given an error so it retried a
 * callback that could never succeed, and a stack trace as the only evidence
 * that a renter had paid.
 *
 * <p>{@code initiate()} had always guarded this: it derives the amount from
 * {@code getBalanceOwed()} and refuses at zero. {@code initiateWithAmount()}
 * took a caller-supplied amount and checked only that it was positive. The
 * asymmetry was the bug, and it was invisible because both methods look
 * equally careful at a glance.
 */
class UnappliedRentPaymentTest {

    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private LeaseRepository leaseRepository;
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    private DarajaService darajaService;
    private DarajaProperties darajaProperties;
    private PlatformDarajaCredentialsResolver darajaResolver;
    private TenantRepository tenantRepository;
    private RentPaymentInitiationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        rentPaymentRequestRepository = mock(RentPaymentRequestRepository.class);
        darajaService = mock(DarajaService.class);
        darajaProperties = mock(DarajaProperties.class);
        darajaResolver = mock(PlatformDarajaCredentialsResolver.class);
        tenantRepository = mock(TenantRepository.class);

        service = new RentPaymentInitiationService(
                rentLedgerEntryRepository, leaseRepository, rentPaymentRequestRepository,
                darajaService, darajaProperties, darajaResolver, tenantRepository);
    }

    private RentLedgerEntry entryWithStatus(RentLedgerStatus status) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getStatus()).thenReturn(status);
        return entry;
    }

    @Test
    void refusesAPartialPaymentAgainstAnAlreadyPaidEntry() {
        RentLedgerEntry paid = entryWithStatus(RentLedgerStatus.PAID);
        when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                .thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.initiateWithAmount(
                tenantId, entryId, new BigDecimal("1"), "+254712345678"))
                .isInstanceOf(RentLedgerStateException.class)
                .hasMessageContaining("already settled");

        // The whole point: no prompt reaches the renter's phone, so no money
        // moves that the ledger would then refuse to record.
        verifyNoInteractions(darajaService);
    }

    /**
     * OVERPAID is settled too. It is reachable through the overpayment
     * resolution flow, and taking another payment against it compounds the
     * problem this system already has to unwind by hand.
     */
    @Test
    void refusesAgainstAnOverpaidEntryToo() {
        RentLedgerEntry overpaid = entryWithStatus(RentLedgerStatus.OVERPAID);
        when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                .thenReturn(Optional.of(overpaid));

        assertThatThrownBy(() -> service.initiateWithAmount(
                tenantId, entryId, new BigDecimal("500"), "+254712345678"))
                .isInstanceOf(RentLedgerStateException.class);

        verifyNoInteractions(darajaService);
    }

    /**
     * The guard must not block the ordinary case it sits in front of — a
     * genuine partial payment against an outstanding charge. Without this,
     * a future tightening could silently turn the fix into an outage.
     */
    @Test
    void stillAllowsAPartialPaymentAgainstAnOutstandingEntry() {
        RentLedgerEntry due = entryWithStatus(RentLedgerStatus.DUE);
        when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId))
                .thenReturn(Optional.of(due));
        when(due.getLeaseId()).thenReturn(UUID.randomUUID());
        // Lease lookup returns empty, so the call fails AFTER the settled
        // check — proving the guard let it through rather than short-circuiting.
        when(leaseRepository.findByIdAndTenantId(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(tenantId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateWithAmount(
                tenantId, entryId, new BigDecimal("500"), "+254712345678"))
                .isInstanceOf(RentLedgerStateException.class)
                .hasMessageContaining("lease not found");
    }
}
