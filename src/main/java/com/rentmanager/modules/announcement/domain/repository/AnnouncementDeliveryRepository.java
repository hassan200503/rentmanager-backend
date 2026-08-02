package com.rentmanager.modules.announcement.domain.repository;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementDeliveryRepository {

    AnnouncementDelivery save(AnnouncementDelivery delivery);

    List<AnnouncementDelivery> saveAll(Collection<AnnouncementDelivery> deliveries);

    /**
     * Rows due for external dispatch (PENDING, or FAILED with the backoff
     * elapsed), ordered by next_attempt_at, capped for one sweep pass.
     * DELIVERED/SENT/SKIPPED_NO_OPTIN rows are never returned.
     */
    List<AnnouncementDelivery> findDue(Instant now, int limit);

    List<AnnouncementDelivery> findByAnnouncementIdAndTenantId(UUID announcementId, UUID tenantId);

    Optional<AnnouncementDelivery> findByAnnouncementIdAndRenterProfileIdAndChannel(
            UUID announcementId, UUID renterProfileId, AnnouncementChannel channel);

    /**
     * The renter's in-app delivery rows for the given announcements - the
     * read state backing the portal list and the landlord read counts.
     */
    List<AnnouncementDelivery> findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
            UUID tenantId, UUID renterProfileId, Collection<UUID> announcementIds, AnnouncementChannel channel);

    /**
     * The renter's unread in-app badge count: IN_APP rows with read_at NULL
     * whose announcement is not yet expired. Expired announcements are
     * hidden from the portal list, so they must not keep the badge lit.
     */
    long countUnreadByTenantIdAndRenterProfileIdAndChannel(
            UUID tenantId, UUID renterProfileId, AnnouncementChannel channel, Instant now);
}
