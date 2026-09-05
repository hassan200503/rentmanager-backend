package com.rentmanager.modules.rentledger.application.autopay;

import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.autopay.AutoPaySettingsRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers only the failure-classification behavior added this session
 * (AutoPaySettings.lastFailureReason) -- AutoPayService/AutoPayScheduler had
 * zero pre-existing test coverage, and backfilling the rest of processAll()
 * is out of scope for this change. No @Mock/@InjectMocks/MockitoExtension
 * per project convention -- manual mock() construction, all domain mocks
 * created before any stubbing.
 */
class AutoPayServiceTest {

    private AutoPaySettingsRepository autoPaySettingsRepository;
    private LeaseRepository leaseRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private TenantProfileRepository tenantProfileRepository;
    private RentPaymentInitiationService rentPaymentInitiationService;
    private SmsService smsService;
    private AutoPayService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        autoPaySettingsRepository = mock(AutoPaySettingsRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        rentPaymentInitiationService = mock(RentPaymentInitiationService.class);
        smsService = mock(SmsService.class);

        service = new AutoPayService(
                autoPaySettingsRepository,
                leaseRepository,
                rentLedgerEntryRepository,
                tenantProfileRepository,
                rentPaymentInitiationService,
                smsService
        );
    }

    private RentLedgerEntry mockDueEntry() {
        return mockEntry(RentLedgerStatus.DUE, java.time.LocalDate.of(2026, 9, 1));
    }

    private RentLedgerEntry mockEntry(RentLedgerStatus status, java.time.LocalDate dueDate) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getId()).thenReturn(UUID.randomUUID());
        when(entry.getStatus()).thenReturn(status);
        when(entry.getDueDate()).thenReturn(dueDate);
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000.00"));
        return entry;
    }

    private AutoPaySettings enabledSettings() {
        AutoPaySettings settings = AutoPaySettings.create(TENANT_ID, LEASE_ID, UUID.randomUUID(), "+254712345678");
        settings.enable();
        when(autoPaySettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings));
        return settings;
    }

    /**
     * Regression guard (2026-09-03): processOne() used to return early
     * unless the entry status was exactly DUE. RentOverdueScheduler flips
     * entries to OVERDUE daily once the grace period lapses, so a renter
     * whose auto-pay failed through that window crossed into OVERDUE and
     * auto-pay never touched the entry again — silently, while the portal
     * still showed the toggle as enabled. Late rent is still that renter's
     * rent, and paying it is what they switched this on for.
     */
    @Test
    void overdueRentIsAutoPaid_notSkipped() {
        enabledSettings();
        RentLedgerEntry overdue = mockEntry(RentLedgerStatus.OVERDUE, java.time.LocalDate.of(2026, 8, 1));
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(overdue));

        service.processAll();

        verify(rentPaymentInitiationService).initiate(TENANT_ID, overdue.getId(), "+254712345678");
    }

    /**
     * Regression guard: the entry was previously chosen with findFirst()
     * over an unordered repository result, so a renter two months behind
     * had whichever row the database happened to return first paid.
     */
    @Test
    void arrearsAreClearedOldestFirst() {
        enabledSettings();
        RentLedgerEntry newer = mockEntry(RentLedgerStatus.DUE, java.time.LocalDate.of(2026, 9, 1));
        RentLedgerEntry older = mockEntry(RentLedgerStatus.OVERDUE, java.time.LocalDate.of(2026, 7, 1));
        // Deliberately newest-first, the order that used to decide the outcome.
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(newer, older));

        service.processAll();

        verify(rentPaymentInitiationService).initiate(TENANT_ID, older.getId(), "+254712345678");
        verify(rentPaymentInitiationService, never()).initiate(TENANT_ID, newer.getId(), "+254712345678");
    }

    @Test
    void aSettledLedgerInitiatesNothing() {
        enabledSettings();
        RentLedgerEntry paid = mock(RentLedgerEntry.class);
        when(paid.getStatus()).thenReturn(RentLedgerStatus.PAID);
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(paid));

        service.processAll();

        verifyNoInteractions(rentPaymentInitiationService);
    }

    @Test
    void darajaFailure_recordsProviderSpecificReason_staysEnabled() {
        AutoPaySettings settings = AutoPaySettings.create(TENANT_ID, LEASE_ID, UUID.randomUUID(), "+254712345678");
        settings.enable();

        when(autoPaySettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings));
        RentLedgerEntry dueEntry = mockDueEntry();
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(dueEntry));
        when(rentPaymentInitiationService.initiate(any(), any(), any()))
                .thenThrow(new DarajaException("Bad Request - Invalid CallBackURL"));
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        service.processAll();

        assertTrue(settings.isEnabled(), "single failure must not disable auto-pay");
        assertEquals(1, settings.getConsecutiveFailures());
        assertTrue(settings.getLastFailureReason().startsWith("M-Pesa couldn't process the request"),
                "DarajaException must map to the M-Pesa-specific reason, not the generic one: "
                        + settings.getLastFailureReason());
        // The raw provider error text must never leak into the renter-facing reason.
        assertFalse(settings.getLastFailureReason().contains("CallBackURL"));
        verify(autoPaySettingsRepository).save(settings);
    }

    @Test
    void genericFailure_recordsGenericReason() {
        AutoPaySettings settings = AutoPaySettings.create(TENANT_ID, LEASE_ID, UUID.randomUUID(), "+254712345678");
        settings.enable();

        when(autoPaySettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings));
        RentLedgerEntry dueEntry = mockDueEntry();
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(dueEntry));
        when(rentPaymentInitiationService.initiate(any(), any(), any()))
                .thenThrow(new RuntimeException("connection reset"));
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        service.processAll();

        assertTrue(settings.getLastFailureReason().startsWith("Auto-pay hit a temporary system issue"));
    }

    @Test
    void thirdConsecutiveFailure_reasonExplainsAutoDisable_notRetry() {
        AutoPaySettings settings = AutoPaySettings.create(TENANT_ID, LEASE_ID, UUID.randomUUID(), "+254712345678");
        settings.enable();
        settings.recordFailure("attempt 1");
        settings.recordFailure("attempt 2");

        when(autoPaySettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings));
        RentLedgerEntry dueEntry = mockDueEntry();
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(dueEntry));
        when(rentPaymentInitiationService.initiate(any(), any(), any()))
                .thenThrow(new RuntimeException("still failing"));
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        service.processAll();

        assertFalse(settings.isEnabled(), "3rd consecutive failure must auto-disable");
        assertTrue(settings.getLastFailureReason().contains("turned off after 3 failed attempts"),
                "the disabling failure's reason must say so, not promise a retry that won't happen: "
                        + settings.getLastFailureReason());
    }

    @Test
    void successAfterFailure_clearsReason() {
        AutoPaySettings settings = AutoPaySettings.create(TENANT_ID, LEASE_ID, UUID.randomUUID(), "+254712345678");
        settings.enable();
        settings.recordFailure("a prior failure");

        when(autoPaySettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings));
        RentLedgerEntry dueEntry = mockDueEntry();
        when(rentLedgerEntryRepository.findByLease(TENANT_ID, LEASE_ID)).thenReturn(List.of(dueEntry));
        when(rentPaymentInitiationService.initiate(any(), any(), any()))
                .thenReturn(mock(com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest.class));
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        service.processAll();

        assertEquals(0, settings.getConsecutiveFailures());
        org.junit.jupiter.api.Assertions.assertNull(settings.getLastFailureReason());
    }
}
