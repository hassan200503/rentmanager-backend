package com.rentmanager.modules.announcement.api.dto.request;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.Set;

/**
 * Compose request. channels defaults to ALL FOUR when absent - the
 * landlord only has to act to deselect paid channels; in-app is always on
 * regardless of what is sent here. expiresAt is optional; expired
 * announcements disappear from the renter portal automatically.
 */
public record CreateAnnouncementRequest(
        @NotBlank(message = "Message is required") String message,
        AnnouncementPriority priority,
        Set<AnnouncementChannel> channels,
        Instant expiresAt
) {
}
