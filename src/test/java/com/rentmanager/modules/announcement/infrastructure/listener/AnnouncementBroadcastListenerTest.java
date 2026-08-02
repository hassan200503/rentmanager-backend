package com.rentmanager.modules.announcement.infrastructure.listener;

import com.rentmanager.modules.announcement.application.AnnouncementDispatchTrigger;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.events.AnnouncementCreated;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fan-out correctness: exactly one delivery row per active renter per
 * selected channel, WhatsApp never attempted without opt-in, in-app
 * delivered at creation, and the immediate dispatch kicked off after the
 * rows are persisted. A failure anywhere in the fan-out can never affect
 * the announcement write (the listener runs AFTER_COMMIT and swallows its
 * own errors - the announcement is already durable).
 */
class AnnouncementBroadcastListenerTest {

    private AnnouncementQueryService queryService;
    private AnnouncementDeliveryRepository deliveryRepository;
    private AnnouncementDispatchTrigger dispatchTrigger;
    private AnnouncementBroadcastListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = mock(AnnouncementQueryService.class);
        deliveryRepository = mock(AnnouncementDeliveryRepository.class);
        dispatchTrigger = mock(AnnouncementDispatchTrigger.class);
        listener = new AnnouncementBroadcastListener(queryService, deliveryRepository, dispatchTrigger);
    }

    @Test
    void createsExactlyOneDeliveryPerRenterPerSelectedChannel() {
        TenantProfile renterA = profile(UUID.randomUUID(), "+254712345678", "a@example.com", false);
        TenantProfile renterB = profile(UUID.randomUUID(), "+254712345679", "b@example.com", true);
        when(queryService.activeProfiles(tenantId)).thenReturn(List.of(renterA, renterB));
        when(deliveryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(dispatchTrigger).dispatchNow();

        listener.onAnnouncementCreated(event(EnumSet.allOf(AnnouncementChannel.class)));

        ArgumentCaptor<List<AnnouncementDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<AnnouncementDelivery> rows = captor.getValue();

        // 2 renters x 4 channels = 8 rows; SMS/EMAIL/WHATSAPP PENDING,
        // IN_APP DELIVERED; renterA WHATSAPP SKIPPED_NO_OPTIN, renterB PENDING.
        assertEquals(8, rows.size());
        assertEquals(5, rows.stream().filter(r -> r.getStatus() == AnnouncementDeliveryStatus.PENDING).count());
        assertEquals(2, rows.stream().filter(r -> r.getStatus() == AnnouncementDeliveryStatus.DELIVERED).count());
        assertEquals(1, rows.stream().filter(r -> r.getStatus() == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN).count());

        for (AnnouncementDelivery row : rows) {
            assertEquals(tenantId, row.getTenantId());
            assertEquals(announcementId, row.getAnnouncementId());
            assertNotNull(row.getRenterProfileId());
        }
    }

    @Test
    void whatsappIsSkippedForEveryRenterWithoutOptIn_neverPending() {
        TenantProfile renterA = profile(UUID.randomUUID(), "+254712345678", "a@example.com", false);
        TenantProfile renterB = profile(UUID.randomUUID(), "+254712345679", "b@example.com", false);
        when(queryService.activeProfiles(tenantId)).thenReturn(List.of(renterA, renterB));
        when(deliveryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(dispatchTrigger).dispatchNow();

        listener.onAnnouncementCreated(event(Set.of(AnnouncementChannel.WHATSAPP)));

        ArgumentCaptor<List<AnnouncementDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<AnnouncementDelivery> rows = captor.getValue();

        assertEquals(2, rows.size(), "one WHATSAPP row per renter");
        assertEquals(2, rows.stream().filter(r -> r.getStatus() == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN).count());
        assertEquals(0, rows.stream().filter(r -> r.getStatus() == AnnouncementDeliveryStatus.PENDING).count(),
                "WhatsApp is never attempted without opt-in, regardless of channel selection");
    }

    @Test
    void deselectedChannelsProduceNoRows() {
        TenantProfile renter = profile(UUID.randomUUID(), "+254712345678", "a@example.com", true);
        when(queryService.activeProfiles(tenantId)).thenReturn(List.of(renter));
        when(deliveryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(dispatchTrigger).dispatchNow();

        listener.onAnnouncementCreated(event(Set.of(AnnouncementChannel.IN_APP, AnnouncementChannel.EMAIL)));

        ArgumentCaptor<List<AnnouncementDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<AnnouncementDelivery> rows = captor.getValue();

        assertEquals(2, rows.size());
        assertEquals(Set.of(AnnouncementChannel.IN_APP, AnnouncementChannel.EMAIL),
                rows.stream().map(AnnouncementDelivery::getChannel).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void kicksOffDispatchImmediatelyAfterPersistingRows() {
        TenantProfile renter = profile(UUID.randomUUID(), "+254712345678", "a@example.com", true);
        when(queryService.activeProfiles(tenantId)).thenReturn(List.of(renter));
        when(deliveryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(dispatchTrigger).dispatchNow();

        listener.onAnnouncementCreated(event(EnumSet.allOf(AnnouncementChannel.class)));

        verify(deliveryRepository).saveAll(any());
        verify(dispatchTrigger).dispatchNow();
    }

    @Test
    void fanOutFailureNeverAffectsTheAnnouncementWrite() {
        when(queryService.activeProfiles(tenantId)).thenThrow(new RuntimeException("db down"));

        // The listener is the AFTER_COMMIT side effect of the announcement
        // write: it must swallow its own failures rather than propagate.
        listener.onAnnouncementCreated(event(EnumSet.allOf(AnnouncementChannel.class)));

        verify(deliveryRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void previewCountsMatchExecutedFanOut() {
        UUID renterAId = UUID.randomUUID();
        UUID renterBId = UUID.randomUUID();
        UUID renterCId = UUID.randomUUID();
        TenantProfile renterA = profile(renterAId, "+254712345678", "a@example.com", true);
        TenantProfile renterB = profile(renterBId, "+254712345679", "b@example.com", false);
        TenantProfile renterC = profile(renterCId, "+254712345680", "c@example.com", false);
        when(queryService.activeProfiles(tenantId)).thenReturn(List.of(renterA, renterB, renterC));
        when(deliveryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(dispatchTrigger).dispatchNow();

        listener.onAnnouncementCreated(event(EnumSet.allOf(AnnouncementChannel.class)));

        ArgumentCaptor<List<AnnouncementDelivery>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryRepository).saveAll(captor.capture());
        List<AnnouncementDelivery> rows = captor.getValue();

        // The preview shows: 3 renters via in-app/SMS/email, 1 via
        // WhatsApp, 2 skipped for WhatsApp. The executed fan-out must
        // produce exactly those numbers as rows.
        assertEquals(3, rows.stream().filter(r -> r.getChannel() == AnnouncementChannel.IN_APP).count());
        assertEquals(3, rows.stream().filter(r -> r.getChannel() == AnnouncementChannel.SMS).count());
        assertEquals(3, rows.stream().filter(r -> r.getChannel() == AnnouncementChannel.EMAIL).count());
        assertEquals(1, rows.stream()
                .filter(r -> r.getChannel() == AnnouncementChannel.WHATSAPP)
                .filter(r -> r.getStatus() == AnnouncementDeliveryStatus.PENDING).count());
        assertEquals(2, rows.stream()
                .filter(r -> r.getChannel() == AnnouncementChannel.WHATSAPP)
                .filter(r -> r.getStatus() == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN).count());
    }

    private AnnouncementCreated event(Set<AnnouncementChannel> channels) {
        return new AnnouncementCreated(tenantId, announcementId, "corr-1", channels);
    }

    private TenantProfile profile(UUID id, String phone, String email, boolean whatsappOptIn) {
        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getPhone()).thenReturn(phone);
        when(profile.getEmail()).thenReturn(email);
        when(profile.isWhatsAppOptIn()).thenReturn(whatsappOptIn);
        return profile;
    }
}
