package com.rentmanager.modules.rentledger.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
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

class RentPaymentNotificationListenerTest {

    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private SmsService smsService;
    private RentPaymentNotificationListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        smsService = mock(SmsService.class);
        listener = new RentPaymentNotificationListener(
                leaseRepository, unitRepository, propertyRepository,
                tenantRepository, tenantProfileRepository, smsService);
    }

    @Test
    void sendsConfirmationToTenantAndNotificationToLandlord() {
        RentPaymentApplied event = new RentPaymentApplied(tenantId, UUID.randomUUID(), "corr",
                leaseId, transactionId, new BigDecimal("15000"), RentLedgerStatus.PAID);

        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101", propertyId);
        Property property = mockProperty("Greenland Apartments");
        Tenant landlord = mockLandlord("+254700000000");

        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).thenReturn(Optional.of(property));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onRentPaymentApplied(event);

        verify(smsService).sendRentPaymentReceivedConfirmation(eq("+254712345678"), eq("15,000"), contains("RCP-"));
        verify(smsService).sendRentPaymentNotificationToLandlord(eq("+254700000000"), eq("John Doe"), eq("15,000"), eq("A101"));
    }

    @Test
    void doesNotSendToLandlord_whenPhoneIsBlank() {
        RentPaymentApplied event = new RentPaymentApplied(tenantId, UUID.randomUUID(), "corr",
                leaseId, transactionId, new BigDecimal("15000"), RentLedgerStatus.PAID);

        Lease lease = mockLease();
        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101", propertyId);
        Property property = mockProperty("X");
        Tenant landlord = mockLandlord("  ");

        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.of(lease));
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).thenReturn(Optional.of(property));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onRentPaymentApplied(event);

        verify(smsService).sendRentPaymentReceivedConfirmation(anyString(), anyString(), anyString());
        verify(smsService, never()).sendRentPaymentNotificationToLandlord(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void swallowsException_whenLeaseNotFound() {
        RentPaymentApplied event = new RentPaymentApplied(tenantId, UUID.randomUUID(), "corr",
                leaseId, transactionId, new BigDecimal("15000"), RentLedgerStatus.PAID);

        when(leaseRepository.findByIdAndTenantId(leaseId, tenantId)).thenReturn(Optional.empty());

        listener.onRentPaymentApplied(event);

        verifyNoInteractions(smsService);
    }

    private Lease mockLease() {
        Lease lease = mock(Lease.class);
        when(lease.getTenantProfileId()).thenReturn(tenantProfileId);
        when(lease.getUnitId()).thenReturn(unitId);
        return lease;
    }

    private TenantProfile mockTenantProfile(String phone, String name) {
        TenantProfile p = mock(TenantProfile.class);
        when(p.getFullName()).thenReturn(name);
        when(p.getPhone()).thenReturn(phone);
        return p;
    }

    private Unit mockUnit(String unitNumber, UUID propertyId) {
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

    private Tenant mockLandlord(String phone) {
        Tenant t = mock(Tenant.class);
        when(t.getPhoneNumber()).thenReturn(phone);
        return t;
    }
}
