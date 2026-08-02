package com.rentmanager.modules.announcement.application;

import com.rentmanager.modules.announcement.api.dto.AnnouncementHistoryItem;
import com.rentmanager.modules.announcement.api.dto.AnnouncementPreviewResponse;
import com.rentmanager.modules.announcement.api.dto.RenterAnnouncementResponse;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Read-side correctness: landlord history reports accurate per-channel
 * sent/delivered/failed/skipped counts and the in-app read count; the
 * renter portal only ever sees their OWN landlord's announcements and
 * expired ones are hidden; viewing sets read_at, which the landlord's
 * read count then reflects; the preview counts match the fan-out
 * planner the broadcast job executes.
 */
class AnnouncementQueryServiceTest {

    private AnnouncementRepository announcementRepository;
    private AnnouncementDeliveryRepository deliveryRepository;
    private LeaseRepository leaseRepository;
    private TenantProfileRepository tenantProfileRepository;
    private AnnouncementQueryService queryService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID renterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        announcementRepository = mock(AnnouncementRepository.class);
        deliveryRepository = mock(AnnouncementDeliveryRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        queryService = new AnnouncementQueryService(
                announcementRepository, deliveryRepository, leaseRepository, tenantProfileRepository);
    }

    // ---------------------------------------------------------------
    // Landlord history stats
    // ---------------------------------------------------------------

    @Test
    void historyReportsAccuratePerChannelCounts() {
        Announcement announcement = Announcement.create(
                tenantA, UUID.randomUUID(), "Lift maintenance Friday",
                AnnouncementPriority.URGENT, EnumSet.allOf(AnnouncementChannel.class), null, "corr");
        UUID announcementId = announcement.getId();
        when(announcementRepository.findAllByTenantId(tenantA)).thenReturn(List.of(announcement));

        UUID renter1 = UUID.randomUUID();
        UUID renter2 = UUID.randomUUID();
        when(deliveryRepository.findByAnnouncementIdAndTenantId(announcementId, tenantA))
                .thenReturn(List.of(
                        delivery(announcementId, renter1, AnnouncementChannel.IN_APP, AnnouncementDeliveryStatus.DELIVERED, Instant.now()),
                        delivery(announcementId, renter2, AnnouncementChannel.IN_APP, AnnouncementDeliveryStatus.DELIVERED, null),
                        delivery(announcementId, renter1, AnnouncementChannel.SMS, AnnouncementDeliveryStatus.SENT, null),
                        delivery(announcementId, renter2, AnnouncementChannel.SMS, AnnouncementDeliveryStatus.FAILED, null),
                        delivery(announcementId, renter1, AnnouncementChannel.EMAIL, AnnouncementDeliveryStatus.DELIVERED, null),
                        delivery(announcementId, renter2, AnnouncementChannel.EMAIL, AnnouncementDeliveryStatus.PENDING, null),
                        delivery(announcementId, renter1, AnnouncementChannel.WHATSAPP, AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN, null)
                ));

        List<AnnouncementHistoryItem> history = queryService.history(tenantA);

        assertEquals(1, history.size());
        AnnouncementHistoryItem item = history.get(0);
        assertEquals(announcementId, item.id());
        assertEquals(2, item.totalRecipients());
        assertEquals(1, item.readCount(), "read count = IN_APP rows with read_at set");
        assertEquals(4, item.stats().size(), "one stat block per selected channel");
        AnnouncementHistoryItem.ChannelStats inApp = channelStats(item, AnnouncementChannel.IN_APP);
        assertEquals(2, inApp.delivered());
        AnnouncementHistoryItem.ChannelStats sms = channelStats(item, AnnouncementChannel.SMS);
        assertEquals(1, sms.sent());
        assertEquals(1, sms.failed());
        AnnouncementHistoryItem.ChannelStats email = channelStats(item, AnnouncementChannel.EMAIL);
        assertEquals(1, email.delivered());
        assertEquals(1, email.pending());
        AnnouncementHistoryItem.ChannelStats whatsapp = channelStats(item, AnnouncementChannel.WHATSAPP);
        assertEquals(1, whatsapp.skippedNoOptIn());
    }

    @Test
    void historyChannelsWithoutDeliveriesReportZero() {
        Announcement announcement = Announcement.create(
                tenantA, UUID.randomUUID(), "Hello", AnnouncementPriority.INFO,
                Set.of(AnnouncementChannel.IN_APP, AnnouncementChannel.SMS), null, "corr");
        UUID announcementId = announcement.getId();
        when(announcementRepository.findAllByTenantId(tenantA)).thenReturn(List.of(announcement));
        when(deliveryRepository.findByAnnouncementIdAndTenantId(announcementId, tenantA)).thenReturn(List.of());

        List<AnnouncementHistoryItem> history = queryService.history(tenantA);

        assertEquals(0, history.get(0).readCount());
        assertEquals(0, channelStats(history.get(0), AnnouncementChannel.SMS).sent());
    }

    // ---------------------------------------------------------------
    // Renter portal: isolation + expiry
    // ---------------------------------------------------------------

    @Test
    void renterSeesOnlyOwnLandlordsAnnouncements() {
        UUID announcementA = UUID.randomUUID();
        UUID announcementB = UUID.randomUUID();
        Announcement own = Announcement.create(
                tenantA, UUID.randomUUID(), "Your landlord's update", AnnouncementPriority.INFO, null, null, "corr");
        Announcement other = Announcement.create(
                tenantB, UUID.randomUUID(), "A different landlord's update", AnnouncementPriority.INFO, null, null, "corr");

        // The repo is tenant-scoped: tenantA's query only returns tenantA's
        // announcement. The service must never ask for tenantB's.
        when(announcementRepository.findActiveByTenantId(eq(tenantA), any(Instant.class))).thenReturn(List.of(own));
        when(announcementRepository.findActiveByTenantId(eq(tenantB), any(Instant.class))).thenReturn(List.of(other));

        when(deliveryRepository.findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                eq(tenantA), eq(renterId), any(), eq(AnnouncementChannel.IN_APP)))
                .thenReturn(List.of(inAppRow(own.getId(), renterId, null)));
        when(deliveryRepository.findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                eq(tenantB), eq(renterId), any(), eq(AnnouncementChannel.IN_APP)))
                .thenReturn(List.of(inAppRow(other.getId(), renterId, null)));

        List<RenterAnnouncementResponse> visible = queryService.renterAnnouncements(tenantA, renterId);
        List<RenterAnnouncementResponse> otherVisible = queryService.renterAnnouncements(tenantB, renterId);

        assertEquals(1, visible.size());
        assertTrue(visible.get(0).message().contains("Your landlord's update"));
        assertEquals(1, otherVisible.size());
        assertTrue(otherVisible.get(0).message().contains("A different landlord's update"));
    }

    @Test
    void expiredAnnouncementsAreHidden() {
        Announcement expired = Announcement.create(
                tenantA, UUID.randomUUID(), "Old news", AnnouncementPriority.INFO,
                null, Instant.now().minusSeconds(3600), "corr");
        Announcement active = Announcement.create(
                tenantA, UUID.randomUUID(), "Current news", AnnouncementPriority.INFO,
                null, Instant.now().plusSeconds(3600), "corr");

        // Repo contract returns only non-expired rows; the service filters
        // again as defense in depth so a stale row can never surface.
        when(announcementRepository.findActiveByTenantId(eq(tenantA), any(Instant.class))).thenReturn(List.of(active, expired));
        when(deliveryRepository.findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                eq(tenantA), eq(renterId), any(), eq(AnnouncementChannel.IN_APP)))
                .thenReturn(List.of(
                        inAppRow(active.getId(), renterId, null),
                        inAppRow(expired.getId(), renterId, null)));

        List<RenterAnnouncementResponse> visible = queryService.renterAnnouncements(tenantA, renterId);

        assertEquals(1, visible.size(), "expired announcement hidden automatically");
        assertEquals("Current news", visible.get(0).message());
    }

    @Test
    void announcementIsHiddenWhenRenterHasNoInAppDeliveryRow() {
        Announcement announcement = Announcement.create(
                tenantA, UUID.randomUUID(), "Broadcast after their lease started",
                AnnouncementPriority.INFO, null, null, "corr");
        when(announcementRepository.findActiveByTenantId(eq(tenantA), any(Instant.class))).thenReturn(List.of(announcement));
        when(deliveryRepository.findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                eq(tenantA), eq(renterId), any(), eq(AnnouncementChannel.IN_APP)))
                .thenReturn(List.of());

        List<RenterAnnouncementResponse> visible = queryService.renterAnnouncements(tenantA, renterId);

        assertTrue(visible.isEmpty(), "no delivery row = not an active renter at broadcast time = not visible");
    }

    @Test
    void renterListCarriesUnreadIndicator() {
        Announcement announcement = Announcement.create(
                tenantA, UUID.randomUUID(), "Hello", AnnouncementPriority.INFO, null, null, "corr");
        UUID announcementId = announcement.getId();
        when(announcementRepository.findActiveByTenantId(eq(tenantA), any(Instant.class))).thenReturn(List.of(announcement));
        when(deliveryRepository.findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                eq(tenantA), eq(renterId), any(), eq(AnnouncementChannel.IN_APP)))
                .thenReturn(List.of(inAppRow(announcementId, renterId, null)));

        List<RenterAnnouncementResponse> visible = queryService.renterAnnouncements(tenantA, renterId);

        assertFalse(visible.get(0).read());
    }

    // ---------------------------------------------------------------
    // Marking read -> landlord read count
    // ---------------------------------------------------------------

    @Test
    void viewingAnnouncementSetsReadAt_andLandlordReadCountReflectsIt() {
        Announcement announcement = Announcement.create(
                tenantA, UUID.randomUUID(), "Hello", AnnouncementPriority.INFO, null, null, "corr");
        UUID announcementId = announcement.getId();
        when(announcementRepository.findByIdAndTenantId(announcementId, tenantA)).thenReturn(Optional.of(announcement));

        AnnouncementDelivery inAppRow = inAppRow(announcementId, renterId, null);
        when(deliveryRepository.findByAnnouncementIdAndRenterProfileIdAndChannel(
                announcementId, renterId, AnnouncementChannel.IN_APP)).thenReturn(Optional.of(inAppRow));
        when(deliveryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RenterAnnouncementResponse response = queryService.markRead(tenantA, renterId, announcementId);

        assertTrue(response.read());
        assertNotNull(inAppRow.getReadAt(), "viewing the announcement sets read_at");
        verify(deliveryRepository).save(inAppRow);

        // The landlord's history read count for this announcement now
        // reports 1 - the same row's read_at drives it.
        when(announcementRepository.findAllByTenantId(tenantA)).thenReturn(List.of(announcement));
        when(deliveryRepository.findByAnnouncementIdAndTenantId(announcementId, tenantA))
                .thenReturn(List.of(inAppRow));
        List<AnnouncementHistoryItem> history = queryService.history(tenantA);
        assertEquals(1, history.get(0).readCount());
    }

    @Test
    void markReadRejectsAnnouncementsOutsideRentersTenant() {
        UUID foreignId = UUID.randomUUID();
        when(announcementRepository.findByIdAndTenantId(foreignId, tenantA)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> queryService.markRead(tenantA, renterId, foreignId));
        verify(deliveryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Unread badge count (renter sidebar)
    // ---------------------------------------------------------------

    @Test
    void unreadCountDelegatesScopedToInAppChannel() {
        when(deliveryRepository.countUnreadByTenantIdAndRenterProfileIdAndChannel(
                eq(tenantA), eq(renterId), eq(AnnouncementChannel.IN_APP), any(Instant.class))).thenReturn(2L);

        long count = queryService.unreadCount(tenantA, renterId);

        assertEquals(2L, count, "badge count comes from the renter's unread IN_APP rows");
        verify(deliveryRepository).countUnreadByTenantIdAndRenterProfileIdAndChannel(
                eq(tenantA), eq(renterId), eq(AnnouncementChannel.IN_APP), any(Instant.class));
    }

    @Test
    void unreadCountIsZeroWhenEverythingIsRead() {
        when(deliveryRepository.countUnreadByTenantIdAndRenterProfileIdAndChannel(
                eq(tenantA), eq(renterId), eq(AnnouncementChannel.IN_APP), any(Instant.class))).thenReturn(0L);

        long count = queryService.unreadCount(tenantA, renterId);

        assertEquals(0L, count);
    }

    // ---------------------------------------------------------------
    // Preview == execution
    // ---------------------------------------------------------------

    @Test
    void previewCountsMatchTheExecutedFanOutPlan() {
        UUID renterAId = UUID.randomUUID();
        UUID renterBId = UUID.randomUUID();
        UUID renterCId = UUID.randomUUID();
        TenantProfile renterA = profile(renterAId, "+254712345678", "a@example.com", true);
        TenantProfile renterB = profile(renterBId, "+254712345679", "b@example.com", false);
        TenantProfile renterC = profile(renterCId, "+254712345680", "c@example.com", false);
        Lease leaseA = lease(renterAId);
        Lease leaseB = lease(renterBId);
        Lease leaseC = lease(renterCId);
        when(leaseRepository.findActiveByTenant(tenantA)).thenReturn(List.of(leaseA, leaseB, leaseC));
        when(tenantProfileRepository.findAllById(any())).thenReturn(List.of(renterA, renterB, renterC));

        AnnouncementPreviewResponse preview = queryService.preview(
                tenantA, EnumSet.allOf(AnnouncementChannel.class));

        assertEquals(3, preview.totalActiveRenters());
        assertEquals(3, preview.inApp());
        assertEquals(3, preview.sms());
        assertEquals(3, preview.email());
        assertEquals(1, preview.whatsapp(), "only opted-in renters counted for WhatsApp");
        assertEquals(2, preview.whatsappSkipped(), "non-opted-in renters are shown as skipped, not counted");
    }

    @Test
    void previewResolvesActiveRentersFromActiveLeasesOnly() {
        UUID activeRenter = UUID.randomUUID();
        TenantProfile profile = profile(activeRenter, "+254712345678", "a@example.com", false);
        Lease activeLease = lease(activeRenter);
        when(leaseRepository.findActiveByTenant(tenantA)).thenReturn(List.of(activeLease));
        when(tenantProfileRepository.findAllById(any())).thenReturn(List.of(profile));

        AnnouncementPreviewResponse preview = queryService.preview(
                tenantA, EnumSet.allOf(AnnouncementChannel.class));

        assertEquals(1, preview.totalActiveRenters());
        assertEquals(1, preview.inApp());
    }

    private Lease lease(UUID renterProfileId) {
        Lease lease = mock(Lease.class);
        when(lease.getTenantProfileId()).thenReturn(renterProfileId);
        when(lease.getStatus()).thenReturn(LeaseStatus.ACTIVE);
        return lease;
    }

    private TenantProfile profile(UUID id, String phone, String email, boolean whatsappOptIn) {
        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getPhone()).thenReturn(phone);
        when(profile.getEmail()).thenReturn(email);
        when(profile.isWhatsAppOptIn()).thenReturn(whatsappOptIn);
        return profile;
    }

    private AnnouncementDelivery delivery(
            UUID announcementId, UUID renterProfileId, AnnouncementChannel channel,
            AnnouncementDeliveryStatus status, Instant readAt) {
        if (status == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN) {
            return AnnouncementDelivery.createSkippedNoOptIn(tenantA, announcementId, renterProfileId);
        }
        AnnouncementDelivery delivery;
        if (channel == AnnouncementChannel.IN_APP) {
            delivery = AnnouncementDelivery.createDelivered(tenantA, announcementId, renterProfileId);
        } else if (status == AnnouncementDeliveryStatus.DELIVERED) {
            delivery = AnnouncementDelivery.rehydrate(
                    UUID.randomUUID(), tenantA, announcementId, renterProfileId, channel,
                    AnnouncementDeliveryStatus.DELIVERED, Instant.now(), null, 0, null, null,
                    Instant.now(), Instant.now(), 0L);
        } else {
            delivery = AnnouncementDelivery.create(tenantA, announcementId, renterProfileId, channel);
            if (status == AnnouncementDeliveryStatus.SENT) {
                delivery.markSent();
            } else if (status == AnnouncementDeliveryStatus.FAILED) {
                delivery.recordFailure("provider error");
            }
        }
        if (readAt != null) {
            delivery.markRead();
        }
        return delivery;
    }

    private AnnouncementDelivery inAppRow(UUID announcementId, UUID renterProfileId, Instant readAt) {
        AnnouncementDelivery row = AnnouncementDelivery.createDelivered(tenantA, announcementId, renterProfileId);
        if (readAt != null) {
            row.markRead();
        }
        return row;
    }

    private AnnouncementHistoryItem.ChannelStats channelStats(AnnouncementHistoryItem item, AnnouncementChannel channel) {
        return item.stats().stream()
                .filter(s -> s.channel() == channel)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stats for " + channel));
    }
}
