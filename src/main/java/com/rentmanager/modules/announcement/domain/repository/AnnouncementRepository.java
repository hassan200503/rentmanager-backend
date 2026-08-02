package com.rentmanager.modules.announcement.domain.repository;

import com.rentmanager.modules.announcement.domain.model.Announcement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementRepository {

    Announcement save(Announcement announcement);

    Optional<Announcement> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Full history for a landlord, newest first - powers the history view
     * with per-channel delivery stats.
     */
    List<Announcement> findAllByTenantId(UUID tenantId);

    /**
     * Non-expired announcements for a landlord, newest first - the renter
     * portal list. Expired ones are hidden automatically.
     */
    List<Announcement> findActiveByTenantId(UUID tenantId, Instant now);
}
