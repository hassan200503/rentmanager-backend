package com.rentmanager.modules.maintenance.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaintenanceRequestNotificationListenerTest {

    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private SmsService smsService;
    private MaintenanceRequestNotificationListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        smsService = mock(SmsService.class);
        listener = new MaintenanceRequestNotificationListener(
                leaseRepository, unitRepository, propertyRepository,
                tenantRepository, tenantProfileRepository, smsService);
    }

    @Test
    void sendsConfirmationToTenantAndNotificationToLandlord_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("+254700000000");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        verify(smsService).sendMaintenanceRequestConfirmation(
                eq("+254712345678"), eq("Leaky tap"), contains("MNT-"));
        verify(smsService).sendMaintenanceRequestNotificationToLandlord(
                eq("+254700000000"), eq("John Doe"), eq("A101"), eq("Leaky tap"));
    }

    @Test
    void doesNotSendToTenant_whenProfilePhoneIsBlank_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("+254700000000");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        verify(smsService, never()).sendMaintenanceRequestConfirmation(anyString(), anyString(), anyString());
        verify(smsService).sendMaintenanceRequestNotificationToLandlord(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void doesNotSendToLandlord_whenLandlordPhoneIsBlank_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("  ");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        verify(smsService).sendMaintenanceRequestConfirmation(anyString(), anyString(), anyString());
        verify(smsService, never()).sendMaintenanceRequestNotificationToLandlord(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendsStatusUpdateToTenant_onStatusChanged() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        verify(smsService).sendMaintenanceRequestStatusUpdate(
                eq("+254712345678"), eq("Maintenance Request"), eq("IN_PROGRESS"));
    }

    @Test
    void doesNotSendStatusUpdate_whenTenantProfileNotFound() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS);

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.empty());

        listener.onMaintenanceRequestStatusChanged(event);

        verifyNoInteractions(smsService);
    }

    @Test
    void doesNotSendStatusUpdate_whenProfilePhoneIsBlank() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS);

        TenantProfile profile = mockTenantProfile("", "John Doe");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        verifyNoInteractions(smsService);
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

    private Tenant mockLandlord(String phone) {
        Tenant t = mock(Tenant.class);
        when(t.getPhoneNumber()).thenReturn(phone);
        return t;
    }
}
