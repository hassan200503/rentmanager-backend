package com.rentmanager.modules.rentledger.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.events.RentOverdueDetected;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RentOverdueNotificationListenerTest {

    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private SmsService smsService;
    private RentOverdueNotificationListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        smsService = mock(SmsService.class);
        listener = new RentOverdueNotificationListener(
                leaseRepository, unitRepository, propertyRepository,
                tenantRepository, tenantProfileRepository,
                rentLedgerEntryRepository, smsService);
    }

    @Test
    void notifiesTheLandlordOnly_becauseTheReminderCadenceOwnsRenterMessaging() {
        RentOverdueDetected event = new RentOverdueDetected(tenantId, entryId, "corr",
                leaseId, tenantProfileId, 3);

        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Property property = mockProperty("X");
        Tenant landlord = mockLandlord("+254700000000");
        RentLedgerEntry entry = mockEntry(new BigDecimal("15000"));

        when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)).thenReturn(Optional.of(entry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).thenReturn(Optional.of(property));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onRentOverdueDetected(event);

        verify(smsService).sendRentOverdueNotificationToLandlord(
                eq("+254700000000"), eq("John Doe"), eq("KSh 15,000.00"), eq("A101"), eq("3"));

        // The renter hears about this from RentReminderService instead, which
        // deduplicates at the database and records what it said. Sending here
        // too would contact them twice for one event: Lease.gracePeriodDays is
        // nullable and treated as zero, so this fires on day +1, exactly where
        // the cadence's OVERDUE_1 milestone sits.
        verify(smsService, never()).sendRentOverdueReminder(anyString(), anyString(), anyString());
    }

    @Test
    void sendsNothingWhenTheLandlordHasNoPhoneNumber() {
        RentOverdueDetected event = new RentOverdueDetected(tenantId, entryId, "corr",
                leaseId, tenantProfileId, 3);

        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Property property = mockProperty("X");
        Tenant landlord = mockLandlord("  ");
        RentLedgerEntry entry = mockEntry(new BigDecimal("15000"));

        when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)).thenReturn(Optional.of(entry));
        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).thenReturn(Optional.of(property));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onRentOverdueDetected(event);

        verify(smsService, never()).sendRentOverdueNotificationToLandlord(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(smsService, never()).sendRentOverdueReminder(anyString(), anyString(), anyString());
    }

    @Test
    void swallowsException_whenLeaseNotFound() {
        RentOverdueDetected event = new RentOverdueDetected(tenantId, entryId, "corr",
                leaseId, tenantProfileId, 3);

        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.empty());

        listener.onRentOverdueDetected(event);

        verifyNoInteractions(smsService);
    }

    private Lease mockLease() {
        Lease lease = mock(Lease.class);
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantId()).thenReturn(tenantId);
        when(lease.getUnitId()).thenReturn(unitId);
        return lease;
    }

    private TenantProfile mockTenantProfile(String phone, String name) {
        TenantProfile p = mock(TenantProfile.class);
        when(p.getFullName()).thenReturn(name);
        when(p.getPhone()).thenReturn(phone);
        return p;
    }

    private Unit mockUnit(String unitNumber) {
        Unit u = mock(Unit.class);
        when(u.getUnitNumber()).thenReturn(unitNumber);
        when(u.getPropertyId()).thenReturn(propertyId);
        return u;
    }

    private Property mockProperty(String name) {
        Property p = mock(Property.class);
        when(p.getName()).thenReturn(name);
        return p;
    }

    private RentLedgerEntry mockEntry(java.math.BigDecimal balanceOwed) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getBalanceOwed()).thenReturn(balanceOwed);
        when(entry.getCurrency()).thenReturn("KES");
        return entry;
    }

    private Tenant mockLandlord(String phone) {
        Tenant t = mock(Tenant.class);
        when(t.getPhoneNumber()).thenReturn(phone);
        return t;
    }
}
