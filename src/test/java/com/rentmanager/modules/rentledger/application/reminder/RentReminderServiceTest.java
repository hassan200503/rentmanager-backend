package com.rentmanager.modules.rentledger.application.reminder;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentReminderPolicyRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RentReminderServiceTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 9, 1);

    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentReminderPolicyRepository policyRepository;
    private RentReminderRecorder recorder;
    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private RentReminderService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        policyRepository = mock(RentReminderPolicyRepository.class);
        recorder = mock(RentReminderRecorder.class);
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        service = new RentReminderService(
                rentLedgerEntryRepository, policyRepository, recorder,
                leaseRepository, unitRepository, tenantRepository, tenantProfileRepository);
    }

    // ── Cadence ──────────────────────────────────────────────────────────

    @Test
    void sendsEmailAtThreeDaysBeforeDue() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan Karungwa");
        when(renter.getPhone()).thenReturn("+254712345678");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isEqualTo(1);
        verify(recorder).recordAndEnqueue(
                eq(tenantId), eq(entryId), eq(leaseId),
                eq(ReminderMilestone.T_MINUS_3), eq(ReminderAudience.RENTER),
                eq(NotificationChannel.EMAIL),
                eq("hassan@example.com"), anyString(), anyString(), anyString(),
                any(), eq("KES"), any(), any());
    }

    /**
     * The behaviour that stops the system contacting somebody every day of
     * the month. Four days overdue matches no milestone.
     */
    @Test
    void sendsNothingOnADayThatMatchesNoMilestone() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.minusDays(4));

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verifyNoInteractions(recorder);
    }

    @Test
    void sweepQueriesOnlyTheWindowTheCadenceCaresAbout() {
        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of());

        service.sweep(RUN_DATE);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(rentLedgerEntryRepository)
                .findAllByStatusInAndDueDateBetween(anyList(), from.capture(), to.capture());

        assertThat(from.getValue()).isEqualTo(RUN_DATE.minusDays(7));
        assertThat(to.getValue()).isEqualTo(RUN_DATE.plusDays(7));
    }

    // ── Skips carried over from the previous scheduler test ──────────────

    @Test
    void skipsEntryWithZeroBalance() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(BigDecimal.ZERO);

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isZero();
        verifyNoInteractions(recorder);
    }

    @Test
    void skipsEntryWhoseLeaseIsMissing() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.empty());

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isZero();
        verifyNoInteractions(recorder);
    }

    @Test
    void skipsAChannelTheRecipientHasNoAddressFor() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan");
        when(renter.getEmail()).thenReturn("   ");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isZero();
        verifyNoInteractions(recorder);
    }

    @Test
    void oneFailingEntryDoesNotStopTheRestOfTheSweep() {
        RentLedgerEntry failing = mock(RentLedgerEntry.class);
        RentLedgerEntry healthy = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);
        UUID healthyEntryId = UUID.randomUUID();

        when(failing.getId()).thenReturn(entryId);
        when(failing.getTenantId()).thenReturn(tenantId);
        when(failing.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(failing.getBalanceOwed()).thenThrow(new IllegalStateException("boom"));

        when(healthy.getId()).thenReturn(healthyEntryId);
        when(healthy.getTenantId()).thenReturn(tenantId);
        when(healthy.getLeaseId()).thenReturn(leaseId);
        when(healthy.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(healthy.getBalanceOwed()).thenReturn(new BigDecimal("10000"));
        when(healthy.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(failing, healthy));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        verify(recorder).recordAndEnqueue(
                any(), eq(healthyEntryId), any(), any(), any(), any(),
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any());
    }

    // ── Idempotency ──────────────────────────────────────────────────────

    /**
     * The unique index rejecting a second attempt is the expected outcome of
     * any re-run, not a failure. It must be counted separately so a rerun
     * does not look like a fault in the logs.
     */
    @Test
    void anAlreadySentReminderIsCountedAsSuchRatherThanAsAFailure() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(recorder).recordAndEnqueue(
                        any(), any(), any(), any(), any(), any(),
                        anyString(), anyString(), anyString(), anyString(),
                        any(), any(), any(), any());

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.alreadySent()).isEqualTo(1);
        assertThat(result.sent()).isZero();
        assertThat(result.failed()).isZero();
    }

    // ── Policy ───────────────────────────────────────────────────────────

    @Test
    void aDisabledMilestoneSendsNothingAndDoesNotLoadTheLease() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of(
                RentReminderPolicy.rehydrate(UUID.randomUUID(), tenantId,
                        ReminderMilestone.T_MINUS_3, false, true, true, false, false, null, null)));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        assertThat(result.sent()).isZero();
        verifyNoInteractions(recorder);
        verify(leaseRepository, never()).findByIdAndTenantId(any(), any());
    }

    /**
     * A landlord with two hundred units would otherwise trigger two hundred
     * identical policy reads in one sweep.
     */
    @Test
    void policiesAreReadOncePerLandlordPerSweep() {
        RentLedgerEntry first = mock(RentLedgerEntry.class);
        RentLedgerEntry second = mock(RentLedgerEntry.class);

        when(first.getId()).thenReturn(entryId);
        when(first.getTenantId()).thenReturn(tenantId);
        when(first.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(first.getBalanceOwed()).thenReturn(new BigDecimal("15000"));

        when(second.getId()).thenReturn(UUID.randomUUID());
        when(second.getTenantId()).thenReturn(tenantId);
        when(second.getDueDate()).thenReturn(RUN_DATE.plusDays(3));
        when(second.getBalanceOwed()).thenReturn(new BigDecimal("20000"));

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(first, second));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of(
                RentReminderPolicy.rehydrate(UUID.randomUUID(), tenantId,
                        ReminderMilestone.T_MINUS_3, false, false, false, false, false, null, null)));

        service.sweep(RUN_DATE);

        verify(policyRepository, times(1)).findByTenant(tenantId);
    }

    // ── Escalation ───────────────────────────────────────────────────────

    @Test
    void escalationMilestoneTextsTheLandlordAsWellAsTheRenter() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);
        Tenant landlord = mock(Tenant.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.minusDays(7));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan Karungwa");
        when(renter.getPhone()).thenReturn("+254712345678");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");
        when(landlord.getPhoneNumber()).thenReturn("+254700111222");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        // Default OVERDUE_7: renter on email + SMS, landlord on SMS.
        assertThat(result.sent()).isEqualTo(3);
        verify(recorder).recordAndEnqueue(
                eq(tenantId), eq(entryId), eq(leaseId),
                eq(ReminderMilestone.OVERDUE_7), eq(ReminderAudience.LANDLORD),
                eq(NotificationChannel.SMS), eq("+254700111222"), anyString(),
                anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void earlierOverdueMilestonesDoNotTellTheLandlord() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE.minusDays(1));
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("15000"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        RentReminderService.SweepResult result = service.sweep(RUN_DATE);

        // OVERDUE_1 default: email to the renter only, no SMS, no landlord.
        assertThat(result.sent()).isEqualTo(1);
        verifyNoInteractions(tenantRepository);
    }

    /**
     * The bug the old overdue path carried: it quoted the full monthly rent
     * instead of what was actually still outstanding.
     */
    @Test
    void quotesTheOutstandingBalanceNotTheFullMonthlyRent() {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        Lease lease = mock(Lease.class);
        TenantProfile renter = mock(TenantProfile.class);
        Unit unit = mock(Unit.class);

        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(RUN_DATE);
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("1500.00"));
        when(entry.getCurrency()).thenReturn("KES");
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        when(renter.getFullName()).thenReturn("Hassan");
        when(renter.getEmail()).thenReturn("hassan@example.com");
        when(unit.getUnitNumber()).thenReturn("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateBetween(anyList(), any(), any()))
                .thenReturn(List.of(entry));
        when(policyRepository.findByTenant(tenantId)).thenReturn(List.of());
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(renter));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        service.sweep(RUN_DATE);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordAndEnqueue(
                any(), any(), any(), any(), any(), eq(NotificationChannel.EMAIL),
                anyString(), anyString(), anyString(), body.capture(),
                eq(new BigDecimal("1500.00")), any(), any(), any());

        assertThat(body.getValue()).contains("KSh 1,500.00");
    }
}
