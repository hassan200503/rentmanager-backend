package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RentReminderSchedulerTest {

    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private SmsService smsService;
    private RentReminderScheduler scheduler;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final LocalDate dueDate = LocalDate.now(ZoneId.of("Africa/Nairobi")).plusDays(3);

    @BeforeEach
    void setUp() {
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        smsService = mock(SmsService.class);
        scheduler = new RentReminderScheduler(rentLedgerEntryRepository, leaseRepository, unitRepository,
                propertyRepository, tenantRepository, tenantProfileRepository, smsService);
    }

    @Test
    void sendsReminderForEntryDueInThreeDays() {
        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, dueDate, new BigDecimal("15000"));
        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678");
        Unit unit = mockUnit("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE), dueDate)).thenReturn(List.of(entry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        scheduler.sendUpcomingPaymentReminders();

        verify(smsService).sendRentUpcomingPaymentReminder(eq("+254712345678"), eq("15,000"), anyString());
    }

    @Test
    void skipsEntryWithZeroBalance() {
        RentLedgerEntry paidEntry = buildDueEntry(tenantId, leaseId, dueDate, BigDecimal.ZERO);

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE), dueDate)).thenReturn(List.of(paidEntry));

        scheduler.sendUpcomingPaymentReminders();

        verifyNoInteractions(smsService);
    }

    @Test
    void skipsEntryWhereLeaseNotFound() {
        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, dueDate, new BigDecimal("15000"));

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE), dueDate)).thenReturn(List.of(entry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.empty());

        scheduler.sendUpcomingPaymentReminders();

        verifyNoInteractions(smsService);
    }

    @Test
    void skipsEntryWhereTenantProfilePhoneIsBlank() {
        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, dueDate, new BigDecimal("15000"));
        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE), dueDate)).thenReturn(List.of(entry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        scheduler.sendUpcomingPaymentReminders();

        verifyNoInteractions(smsService);
    }

    @Test
    void continuesWhenOneEntryFails() {
        RentLedgerEntry failingEntry = buildDueEntry(tenantId, leaseId, dueDate, new BigDecimal("15000"));
        RentLedgerEntry successEntry = buildDueEntry(tenantId, leaseId, dueDate, new BigDecimal("10000"));
        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678");
        Unit unit = mockUnit("A101");

        when(rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE), dueDate)).thenReturn(List.of(failingEntry, successEntry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));

        scheduler.sendUpcomingPaymentReminders();

        verify(smsService).sendRentUpcomingPaymentReminder(anyString(), eq("10,000"), anyString());
    }

    private RentLedgerEntry buildDueEntry(UUID tenantId, UUID leaseId, LocalDate dueDate, BigDecimal balance) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(dueDate);
        when(entry.getBalanceOwed()).thenReturn(balance);
        when(entry.getStatus()).thenReturn(RentLedgerStatus.DUE);
        return entry;
    }

    private Lease mockLease() {
        Lease lease = mock(Lease.class);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        return lease;
    }

    private TenantProfile mockTenantProfile(String phone) {
        TenantProfile p = mock(TenantProfile.class);
        when(p.getPhone()).thenReturn(phone);
        return p;
    }

    private Unit mockUnit(String unitNumber) {
        Unit u = mock(Unit.class);
        when(u.getUnitNumber()).thenReturn(unitNumber);
        when(u.getPropertyId()).thenReturn(propertyId);
        return u;
    }
}
