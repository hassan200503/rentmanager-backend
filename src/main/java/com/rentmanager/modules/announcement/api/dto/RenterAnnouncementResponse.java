package com.rentmanager.modules.announcement.api.dto;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One announcement in the renter portal list: newest first, expired ones
 * already filtered server-side, with the renter's own read state attached
 * (from their IN_APP delivery row).
 */
public record RenterAnnouncementResponse(
        UUID id,
        String message,
        AnnouncementPriority priority,
        Instant createdAt,
        Instant expiresAt,
        boolean read
) {
    public static RenterAnnouncementResponse from(Announcement announcement, boolean read) {
        return new RenterAnnouncementResponse(
                announcement.getId(),
                announcement.getMessage(),
                announcement.getPriority(),
                announcement.getCreatedAt(),
                announcement.getExpiresAt(),
                read
        );
    }

    public static List<RenterAnnouncementResponse> from(
            List<Announcement> announcements,
            java.util.function.Function<UUID, Boolean> readState
    ) {
        return announcements.stream()
                .map(a -> from(a, Boolean.TRUE.equals(readState.apply(a.getId()))))
                .toList();
    }
}
