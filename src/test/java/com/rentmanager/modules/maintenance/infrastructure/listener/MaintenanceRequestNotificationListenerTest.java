package com.rentmanager.modules.maintenance.infrastructure.listener;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
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
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MaintenanceRequestNotificationListenerTest {

    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private TenantProfileRepository tenantProfileRepository;
    private NotificationDeliveryRepository notificationDeliveryRepository;
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
        notificationDeliveryRepository = mock(NotificationDeliveryRepository.class);
        listener = new MaintenanceRequestNotificationListener(
                leaseRepository, unitRepository, propertyRepository,
                tenantRepository, tenantProfileRepository, notificationDeliveryRepository);
    }

    @Test
    void enqueuesSmsToRenterAndSmsWhatsappEmailToLandlord_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("+254700000000", "landlord@example.com");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, times(4)).save(captor.capture());

        List<NotificationDelivery> deliveries = captor.getAllValues();
        NotificationDelivery renterSms = deliveries.stream()
                .filter(d -> d.getChannel() == NotificationChannel.SMS)
                .filter(d -> d.getRecipient().equals("+254712345678"))
                .findFirst()
                .orElseThrow();
        assertTrue(renterSms.getMessage().contains("Leaky tap"));
        assertTrue(renterSms.getMessage().contains("MNT-"));
        assertSame(event.getEventId(), renterSms.getEventId());

        NotificationDelivery landlordSms = deliveries.stream()
                .filter(d -> d.getChannel() == NotificationChannel.SMS)
                .filter(d -> d.getRecipient().equals("+254700000000"))
                .findFirst()
                .orElseThrow();
        assertTrue(landlordSms.getMessage().contains("John Doe"));
        assertTrue(landlordSms.getMessage().contains("A101"));

        assertTrue(deliveries.stream().anyMatch(d ->
                d.getChannel() == NotificationChannel.WHATSAPP
                        && d.getRecipient().equals("+254700000000")));

        NotificationDelivery landlordEmail = deliveries.stream()
                .filter(d -> d.getChannel() == NotificationChannel.EMAIL)
                .findFirst()
                .orElseThrow();
        assertEquals("landlord@example.com", landlordEmail.getRecipient());
        assertTrue(landlordEmail.getSubject().contains("Leaky tap"));
    }

    @Test
    void skipsRenterSms_whenProfilePhoneIsBlank_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("+254700000000", "landlord@example.com");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository, times(3)).save(captor.capture());

        assertTrue(captor.getAllValues().stream()
                .noneMatch(d -> d.getRecipient().equals("+254712345678") && d.getChannel() == NotificationChannel.SMS));
    }

    @Test
    void skipsLandlordChannels_whenLandlordPhoneIsBlank_onSubmitted() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("  ", null);

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));

        listener.onMaintenanceRequestSubmitted(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(captor.capture());

        List<NotificationDelivery> deliveries = captor.getAllValues();
        assertEquals(1, deliveries.size());
        assertEquals(NotificationChannel.SMS, deliveries.get(0).getChannel());
        assertEquals("+254712345678", deliveries.get(0).getRecipient());
    }

    @Test
    void enqueuesStatusUpdateToRenter_onStatusChanged() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS, "Water tank locked", null);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(captor.capture());

        NotificationDelivery delivery = captor.getValue();
        assertEquals(NotificationChannel.SMS, delivery.getChannel());
        assertEquals("+254712345678", delivery.getRecipient());
        assertTrue(delivery.getMessage().toLowerCase().contains("in progress"));
    }

    /**
     * What the renter actually receives.
     *
     * <p>This message used to read: {@code Update on "Maintenance Request":
     * in review. Log in to your RentManager portal for details.} A renter
     * with several open reports could not tell which one had moved, and the
     * portal it pointed them to showed the same status and nothing more. The
     * title identifies the request; the landlord's note is the only part that
     * answers what they asked.
     */
    @Test
    void theRenterSmsNamesTheRequestAndCarriesTheLandlordsReply() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.SCHEDULED,
                "Water tank locked", "Plumber booked for Thursday morning, he has the key.");

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(captor.capture());

        String message = captor.getValue().getMessage();
        assertTrue(message.contains("Water tank locked"),
                "the renter must be able to tell WHICH request moved: " + message);
        assertTrue(message.contains("Plumber booked for Thursday"),
                "the landlord's own words are the point of the reply: " + message);
        assertTrue(message.toLowerCase().contains("scheduled"),
                "the status still belongs in the message: " + message);
    }

    /** No reply is a normal case: the status alone must still send cleanly. */
    @Test
    void theRenterSmsStillWorksWhenTheLandlordSaidNothing() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.COMPLETED,
                "Leaking taps", null);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        ArgumentCaptor<NotificationDelivery> captor = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(notificationDeliveryRepository).save(captor.capture());

        String message = captor.getValue().getMessage();
        assertTrue(message.contains("Leaking taps"), message);
        assertTrue(message.toLowerCase().contains("completed"), message);
        assertFalse(message.contains("null"), "a missing note must not leak the word null: " + message);
    }

    @Test
    void enqueuesNothing_whenTenantProfileNotFound_onStatusChanged() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS, "Water tank locked", null);

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.empty());

        listener.onMaintenanceRequestStatusChanged(event);

        verifyNoInteractions(notificationDeliveryRepository);
    }

    @Test
    void enqueuesNothing_whenProfilePhoneIsBlank_onStatusChanged() {
        MaintenanceRequestStatusChanged event = new MaintenanceRequestStatusChanged(
                tenantId, requestId, "corr", tenantProfileId,
                MaintenanceRequestStatus.SUBMITTED, MaintenanceRequestStatus.IN_PROGRESS, "Water tank locked", null);

        TenantProfile profile = mockTenantProfile("", "John Doe");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        listener.onMaintenanceRequestStatusChanged(event);

        verifyNoInteractions(notificationDeliveryRepository);
    }

    @Test
    void doesNotThrow_whenEnqueueFails() {
        MaintenanceRequestSubmitted event = new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, "Leaky tap", MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        TenantProfile profile = mockTenantProfile("+254712345678", "John Doe");
        Unit unit = mockUnit("A101");
        Tenant landlord = mockLandlord("+254700000000", "landlord@example.com");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findByIdAndTenantId(unitId, tenantId)).thenReturn(Optional.of(unit));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(notificationDeliveryRepository.save(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(() -> listener.onMaintenanceRequestSubmitted(event));
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

    private Tenant mockLandlord(String phone, String email) {
        Tenant t = mock(Tenant.class);
        when(t.getPhoneNumber()).thenReturn(phone);
        when(t.getEmail()).thenReturn(email);
        return t;
    }
}
