package com.rentmanager.modules.announcement.application;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.modules.notification.application.NotificationDispatchService;
import com.rentmanager.modules.notification.email.EmailService;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.notification.whatsapp.WhatsAppService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dispatch correctness: SMS/email carry the FULL untruncated message,
 * WhatsApp carries the templated truncated version, one failed
 * recipient/channel never blocks the rest of the broadcast, SENT rows are
 * never re-dispatched, and the announcement write is never affected by
 * dispatch outcomes.
 */
class AnnouncementDispatchSweepServiceTest {

    private AnnouncementDeliveryRepository deliveryRepository;
    private AnnouncementRepository announcementRepository;
    private TenantProfileRepository tenantProfileRepository;
    private TenantRepository tenantRepository;
    private SmsService smsService;
    private EmailService emailService;
    private WhatsAppService whatsAppService;
    private AnnouncementDispatchSweepService sweepService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID renterId = UUID.randomUUID();
    private static final String FULL_MESSAGE = "Rent will be collected via M-PESA on the 1st of every month. Payday reminders now include bank holidays.";
    private static final String PHONE = "+254712345678";

    @BeforeEach
    void setUp() {
        deliveryRepository = mock(AnnouncementDeliveryRepository.class);
        announcementRepository = mock(AnnouncementRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        tenantRepository = mock(TenantRepository.class);
        smsService = mock(SmsService.class);
        emailService = mock(EmailService.class);
        whatsAppService = mock(WhatsAppService.class);
        NotificationDispatchService dispatchService = new NotificationDispatchService(smsService, emailService, whatsAppService,
                mock(com.rentmanager.modules.notification.push.application.PushNotificationService.class));
        AnnouncementProperties properties = new AnnouncementProperties();
        properties.setWhatsappMaxChars(30);
        AnnouncementWhatsAppTemplate template = new AnnouncementWhatsAppTemplate(properties);
        sweepService = new AnnouncementDispatchSweepService(
                deliveryRepository, announcementRepository, tenantProfileRepository,
                tenantRepository, dispatchService, template, properties);
    }

    @Test
    void smsCarriesFullUntruncatedMessage() {
        AnnouncementDelivery delivery = delivery(AnnouncementChannel.SMS);
        Announcement announcement = announcement();
        TenantProfile profile = profile();
        Tenant landlord = landlord();
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.of(announcement));
        when(tenantProfileRepository.findById(renterId)).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(smsService.sendRaw(any(), any())).thenReturn(true);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        verify(smsService).sendRaw(eq(PHONE), contains(FULL_MESSAGE));
        ArgumentCaptor<AnnouncementDelivery> captor = ArgumentCaptor.forClass(AnnouncementDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertEquals(AnnouncementDeliveryStatus.SENT, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getSentAt());
    }

    @Test
    void emailCarriesFullUntruncatedMessageWithLandlordSubject() {
        AnnouncementDelivery delivery = delivery(AnnouncementChannel.EMAIL);
        Announcement announcement = announcement();
        TenantProfile profile = profile();
        Tenant landlord = landlord();
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.of(announcement));
        when(tenantProfileRepository.findById(renterId)).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        doNothing().when(emailService).send(any(), any(), any());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        verify(emailService).send(eq("renter@example.com"), eq("New announcement from Acme Properties"), contains(FULL_MESSAGE));
    }

    @Test
    void whatsappUsesTemplatedTruncatedMessage() {
        AnnouncementDelivery delivery = delivery(AnnouncementChannel.WHATSAPP);
        Announcement announcement = announcement();
        TenantProfile profile = profile();
        Tenant landlord = landlord();
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.of(announcement));
        when(tenantProfileRepository.findById(renterId)).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sweepService.dispatchDue();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppService).sendTemplate(eq(PHONE), eq("announcement_utility_v1"), messageCaptor.capture());
        String sent = messageCaptor.getValue();
        assertTrue(sent.startsWith("New announcement from Acme Properties: "));
        assertTrue(sent.endsWith("View full details in the RentManager app."));
        assertTrue(sent.contains("…"), "message truncated to the template limit");
        assertTrue(!sent.contains(FULL_MESSAGE), "WhatsApp never carries the full free text");
    }

    @Test
    void oneFailedChannelDoesNotBlockTheRestOfTheBroadcast() {
        AnnouncementDelivery failing = delivery(AnnouncementChannel.WHATSAPP);
        AnnouncementDelivery succeeding = delivery(AnnouncementChannel.SMS);
        Announcement announcement = announcement();
        TenantProfile profile = profile();
        Tenant landlord = landlord();
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(failing, succeeding));
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.of(announcement));
        when(tenantProfileRepository.findById(renterId)).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        doThrow(new RuntimeException("whatsapp api down")).when(whatsAppService).sendTemplate(any(), any(), any());
        when(smsService.sendRaw(any(), any())).thenReturn(true);
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int dispatched = sweepService.dispatchDue();

        assertEquals(2, dispatched);
        ArgumentCaptor<AnnouncementDelivery> captor = ArgumentCaptor.forClass(AnnouncementDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(AnnouncementDeliveryStatus.FAILED, captor.getAllValues().get(0).getStatus());
        assertEquals(AnnouncementDeliveryStatus.SENT, captor.getAllValues().get(1).getStatus());
    }

    @Test
    void sentRowsAreNeverDispatchedAgain() {
        AnnouncementDelivery sent = delivery(AnnouncementChannel.SMS);
        sent.markSent();
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of());

        int dispatched = sweepService.dispatchDue();

        assertEquals(0, dispatched);
        verify(announcementRepository, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void failedRowIsRetriedOnceBackoffElapsed_neverDoubleSendsSentRows() {
        AnnouncementDelivery delivery = delivery(AnnouncementChannel.SMS);
        Announcement announcement = announcement();
        TenantProfile profile = profile();
        Tenant landlord = landlord();
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.of(announcement));
        when(tenantProfileRepository.findById(renterId)).thenReturn(Optional.of(profile));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(landlord));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(smsService.sendRaw(any(), any())).thenReturn(false);
        sweepService.dispatchDue();
        assertEquals(AnnouncementDeliveryStatus.FAILED, delivery.getStatus(), "first pass fails");
        assertEquals(1, delivery.getAttemptCount());

        // Backoff elapses; second pass succeeds. The provider call happens
        // exactly once on the retry (not twice), and the row ends SENT.
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(smsService.sendRaw(any(), any())).thenReturn(true);
        sweepService.dispatchDue();

        assertEquals(AnnouncementDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());
        verify(smsService, org.mockito.Mockito.times(2)).sendRaw(any(), any());
    }

    @Test
    void missingAnnouncementRecordsFailureWithoutThrowing() {
        AnnouncementDelivery delivery = delivery(AnnouncementChannel.SMS);
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of(delivery));
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantId)).thenReturn(Optional.empty());
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int dispatched = sweepService.dispatchDue();

        assertEquals(1, dispatched);
        verify(smsService, never()).sendRaw(any(), any());
        ArgumentCaptor<AnnouncementDelivery> captor = ArgumentCaptor.forClass(AnnouncementDelivery.class);
        verify(deliveryRepository).save(captor.capture());
        assertTrue(captor.getValue().getLastError().contains("not found"));
    }

    @Test
    void doesNothingWhenNoDueDeliveries() {
        when(deliveryRepository.findDue(any(Instant.class), anyInt())).thenReturn(List.of());

        int dispatched = sweepService.dispatchDue();

        assertEquals(0, dispatched);
        verify(deliveryRepository, never()).save(any());
    }

    private Announcement announcement() {
        return Announcement.create(
                tenantId, UUID.randomUUID(), FULL_MESSAGE,
                com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority.INFO,
                java.util.EnumSet.allOf(AnnouncementChannel.class), null, "corr");
    }

    private TenantProfile profile() {
        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getPhone()).thenReturn(PHONE);
        when(profile.getEmail()).thenReturn("renter@example.com");
        return profile;
    }

    private Tenant landlord() {
        Tenant landlord = mock(Tenant.class);
        when(landlord.getName()).thenReturn("Acme Properties");
        return landlord;
    }

    private AnnouncementDelivery delivery(AnnouncementChannel channel) {
        return AnnouncementDelivery.create(tenantId, announcementId, renterId, channel);
    }
}
