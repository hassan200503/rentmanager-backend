package com.rentmanager.modules.announcement.api.dto;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Compose-confirmation response: the persisted announcement. The delivery
 * fan-out happens after commit via the broadcast listener - the landlord
 * sees the recipient preview on the confirm screen, and the history view
 * shows the actual per-channel outcomes.
 */
public record AnnouncementResponse(
        UUID id,
        String message,
        AnnouncementPriority priority,
        Set<AnnouncementChannel> channels,
        Instant expiresAt,
        Instant createdAt
) {
    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getMessage(),
                announcement.getPriority(),
                announcement.getChannels(),
                announcement.getExpiresAt(),
                announcement.getCreatedAt()
        );
    }

    public static List<AnnouncementResponse> from(List<Announcement> announcements) {
        return announcements.stream().map(AnnouncementResponse::from).toList();
    }
}
