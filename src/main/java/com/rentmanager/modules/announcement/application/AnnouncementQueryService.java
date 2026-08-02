package com.rentmanager.modules.announcement.application;

import com.rentmanager.modules.announcement.api.dto.AnnouncementHistoryItem;
import com.rentmanager.modules.announcement.api.dto.AnnouncementPreviewResponse;
import com.rentmanager.modules.announcement.api.dto.RenterAnnouncementResponse;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.modules.announcement.domain.service.AnnouncementFanoutPlanner;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Read side of announcements: the send preview, the landlord history with
 * per-channel delivery stats, and the renter portal list with read state.
 * Renter-side ids are never taken from the client - the calling layer
 * (TenantPortalService) resolves the renter profile from the
 * authenticated user, and every query is tenant-scoped.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementQueryService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementDeliveryRepository deliveryRepository;
    private final LeaseRepository leaseRepository;
    private final TenantProfileRepository tenantProfileRepository;

    /**
     * Recipient + channel counts for the confirm screen. Computed from the
     * same active-renter set and the same planner the broadcast listener
     * uses, so the preview matches what the job actually executes.
     */
    @Transactional(readOnly = true)
    public AnnouncementPreviewResponse preview(UUID tenantId, Set<AnnouncementChannel> channels) {
        List<TenantProfile> activeProfiles = activeProfiles(tenantId);
        List<AnnouncementFanoutPlanner.Target> targets =
                AnnouncementFanoutPlanner.plan(channels, activeProfiles);

        int inApp = 0;
        int sms = 0;
        int email = 0;
        int whatsapp = 0;
        int skipped = 0;
        for (AnnouncementFanoutPlanner.Target target : targets) {
            switch (target.channel()) {
                case IN_APP -> inApp++;
                case SMS -> sms++;
                case EMAIL -> email++;
                case WHATSAPP -> {
                    if (target.isSkipped()) {
                        skipped++;
                    } else {
                        whatsapp++;
                    }
                }
            }
        }
        return new AnnouncementPreviewResponse(activeProfiles.size(), inApp, sms, email, whatsapp, skipped);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementHistoryItem> history(UUID tenantId) {
        List<Announcement> announcements = announcementRepository.findAllByTenantId(tenantId);
        return announcements.stream()
                .map(announcement -> AnnouncementHistoryItem.from(
                        announcement,
                        deliveryRepository.findByAnnouncementIdAndTenantId(announcement.getId(), tenantId)))
                .toList();
    }

    /**
     * Renter portal list: only the renter's OWN landlord's announcements
     * (tenant-scoped), non-expired, newest first, with the renter's read
     * state. An announcement is only visible to a renter who has an IN_APP
     * delivery row - i.e. was an active renter at broadcast time.
     */
    @Transactional(readOnly = true)
    public List<RenterAnnouncementResponse> renterAnnouncements(UUID tenantId, UUID renterProfileId) {
        List<Announcement> active = announcementRepository.findActiveByTenantId(tenantId, Instant.now());
        if (active.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = active.stream().map(Announcement::getId).toList();
        List<AnnouncementDelivery> inAppRows = deliveryRepository
                .findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                        tenantId, renterProfileId, ids, AnnouncementChannel.IN_APP);

        Set<UUID> visibleIds = new HashSet<>();
        Map<UUID, Boolean> readState = new HashMap<>();
        for (AnnouncementDelivery row : inAppRows) {
            visibleIds.add(row.getAnnouncementId());
            readState.put(row.getAnnouncementId(), row.getReadAt() != null);
        }

        return active.stream()
                .filter(a -> visibleIds.contains(a.getId()))
                .filter(a -> !a.isExpired(Instant.now()))
                .map(a -> RenterAnnouncementResponse.from(a, Boolean.TRUE.equals(readState.get(a.getId()))))
                .toList();
    }

    /**
     * Renter opened an announcement: marks the IN_APP delivery row's
     * read_at (first view wins). Tenant-isolated: the announcement must
     * belong to the renter's own landlord, and the renter must have an
     * IN_APP row for it.
     */
    @Transactional
    public RenterAnnouncementResponse markRead(UUID tenantId, UUID renterProfileId, UUID announcementId) {
        Announcement announcement = announcementRepository.findByIdAndTenantId(announcementId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Announcement not found: " + announcementId));

        AnnouncementDelivery inAppRow = deliveryRepository
                .findByAnnouncementIdAndRenterProfileIdAndChannel(
                        announcementId, renterProfileId, AnnouncementChannel.IN_APP)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No in-app delivery for announcement: " + announcementId));

        inAppRow.markRead();
        deliveryRepository.save(inAppRow);

        return RenterAnnouncementResponse.from(announcement, true);
    }

    /**
     * Renter portal unread badge count: IN_APP deliveries with read_at NULL,
     * restricted to announcements that have not expired (expired ones are
     * hidden from the portal list, so they must not keep the badge lit).
     */
    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId, UUID renterProfileId) {
        return deliveryRepository.countUnreadByTenantIdAndRenterProfileIdAndChannel(
                tenantId, renterProfileId, AnnouncementChannel.IN_APP, Instant.now());
    }

    /**
     * Active renters across the portfolio: tenants with an ACTIVE lease,
     * one profile each (a renter with two active leases is still one
     * recipient - one message per person, not per lease).
     */
    public List<TenantProfile> activeProfiles(UUID tenantId) {
        Set<UUID> profileIds = new HashSet<>();
        for (Lease lease : leaseRepository.findActiveByTenant(tenantId)) {
            if (lease.getTenantProfileId() != null) {
                profileIds.add(lease.getTenantProfileId());
            }
        }
        if (profileIds.isEmpty()) {
            return List.of();
        }
        return tenantProfileRepository.findAllById(profileIds);
    }
}
