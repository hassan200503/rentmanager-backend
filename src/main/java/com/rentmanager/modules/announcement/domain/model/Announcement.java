package com.rentmanager.modules.announcement.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.events.AnnouncementCreated;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A landlord broadcast: one message composed once, delivered to every
 * active renter across the portfolio. Holds the FULL message text - SMS,
 * email and in-app carry it verbatim; only the WhatsApp leg is templated
 * and truncated (see AnnouncementWhatsAppTemplate).
 *
 * The write of this aggregate always succeeds regardless of what happens
 * downstream in the fan-out: delivery rows are created after commit by
 * the broadcast listener and dispatched independently per recipient per
 * channel.
 */
public class Announcement extends AggregateRoot {

    private UUID authorId;
    private String message;
    private AnnouncementPriority priority;
    private Set<AnnouncementChannel> channels;
    private Instant expiresAt;

    protected Announcement() {
    }

    public static Announcement create(
            UUID tenantId,
            UUID authorId,
            String message,
            AnnouncementPriority priority,
            Set<AnnouncementChannel> channels,
            Instant expiresAt,
            String correlationId
    ) {
        if (authorId == null) {
            throw new IllegalArgumentException("authorId is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (priority == null) {
            throw new IllegalArgumentException("priority is required");
        }

        Announcement announcement = new Announcement();
        announcement.setId(UUID.randomUUID());
        announcement.assignTenant(tenantId);
        announcement.authorId = authorId;
        announcement.message = message.trim();
        announcement.priority = priority;
        announcement.channels = normalizeChannels(channels);
        announcement.expiresAt = expiresAt;

        announcement.registerEvent(new AnnouncementCreated(
                tenantId,
                announcement.getId(),
                correlationId,
                announcement.channels
        ));

        return announcement;
    }

    public static Announcement rehydrate(
            UUID id,
            UUID tenantId,
            UUID authorId,
            String message,
            AnnouncementPriority priority,
            Set<AnnouncementChannel> channels,
            Instant expiresAt,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        Announcement announcement = new Announcement();
        announcement.restoreId(id);
        announcement.assignTenantIfUnset(tenantId);
        announcement.authorId = authorId;
        announcement.message = message;
        announcement.priority = priority;
        announcement.channels = normalizeChannels(channels);
        announcement.expiresAt = expiresAt;
        announcement.restoreCreatedAt(createdAt);
        announcement.restoreUpdatedAt(updatedAt);
        announcement.setVersion(version);
        return announcement;
    }

    /**
     * IN_APP is always on: a landlord cannot compose an announcement that
     * is not visible in the renter portal, even if every paid channel is
     * deselected.
     */
    private static Set<AnnouncementChannel> normalizeChannels(Set<AnnouncementChannel> requested) {
        Set<AnnouncementChannel> normalized = new LinkedHashSet<>();
        normalized.add(AnnouncementChannel.IN_APP);
        if (requested != null) {
            for (AnnouncementChannel channel : requested) {
                if (channel != null) {
                    normalized.add(channel);
                }
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getMessage() {
        return message;
    }

    public AnnouncementPriority getPriority() {
        return priority;
    }

    public Set<AnnouncementChannel> getChannels() {
        return channels;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
