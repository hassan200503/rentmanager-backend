package com.rentmanager.modules.announcement.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;

import java.util.Set;
import java.util.UUID;

/**
 * Fired once per composed announcement, AFTER_COMMIT. The fan-out listener
 * resolves the active renter list and creates one AnnouncementDelivery row
 * per renter per selected channel - never synchronously in the HTTP
 * request that wrote the announcement.
 */
public class AnnouncementCreated extends DomainEvent {

    private final UUID announcementId;
    private final Set<AnnouncementChannel> channels;

    public AnnouncementCreated(
            UUID tenantId,
            UUID announcementId,
            String correlationId,
            Set<AnnouncementChannel> channels
    ) {
        super(tenantId, announcementId, correlationId);
        this.announcementId = announcementId;
        this.channels = channels;
    }

    public UUID getAnnouncementId() {
        return announcementId;
    }

    public Set<AnnouncementChannel> getChannels() {
        return channels;
    }

    @Override
    public String eventType() {
        return "AnnouncementCreated";
    }
}
