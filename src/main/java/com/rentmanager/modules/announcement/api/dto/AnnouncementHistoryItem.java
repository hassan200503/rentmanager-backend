package com.rentmanager.modules.announcement.api.dto;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One row of the landlord history view: the announcement plus per-channel
 * delivery outcomes (sent/delivered/failed/skipped-no-optin/pending) and
 * the in-app read count.
 */
public record AnnouncementHistoryItem(
        UUID id,
        String message,
        com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority priority,
        Set<AnnouncementChannel> channels,
        Instant expiresAt,
        Instant createdAt,
        List<ChannelStats> stats,
        int readCount,
        int totalRecipients
) {
    public record ChannelStats(
            AnnouncementChannel channel,
            int pending,
            int sent,
            int delivered,
            int failed,
            int skippedNoOptIn
    ) {
        static ChannelStats empty(AnnouncementChannel channel) {
            return new ChannelStats(channel, 0, 0, 0, 0, 0);
        }

        static ChannelStats aggregate(AnnouncementChannel channel, List<AnnouncementDelivery> deliveries) {
            int pending = 0;
            int sent = 0;
            int delivered = 0;
            int failed = 0;
            int skipped = 0;
            for (AnnouncementDelivery delivery : deliveries) {
                switch (delivery.getStatus()) {
                    case PENDING -> pending++;
                    case SENT -> sent++;
                    case DELIVERED -> delivered++;
                    case FAILED -> failed++;
                    case SKIPPED_NO_OPTIN -> skipped++;
                }
            }
            return new ChannelStats(channel, pending, sent, delivered, failed, skipped);
        }
    }

    public static AnnouncementHistoryItem from(Announcement announcement, List<AnnouncementDelivery> deliveries) {
        Map<AnnouncementChannel, List<AnnouncementDelivery>> byChannel = new EnumMap<>(AnnouncementChannel.class);
        for (AnnouncementDelivery delivery : deliveries) {
            byChannel.computeIfAbsent(delivery.getChannel(), c -> new java.util.ArrayList<>()).add(delivery);
        }

        Set<AnnouncementChannel> channels = EnumSet.copyOf(announcement.getChannels());
        List<ChannelStats> stats = channels.stream()
                .map(channel -> byChannel.containsKey(channel)
                        ? ChannelStats.aggregate(channel, byChannel.get(channel))
                        : ChannelStats.empty(channel))
                .toList();

        int readCount = (int) byChannel.getOrDefault(AnnouncementChannel.IN_APP, List.of()).stream()
                .filter(d -> d.getReadAt() != null)
                .count();

        Set<UUID> recipients = new java.util.HashSet<>();
        for (AnnouncementDelivery delivery : deliveries) {
            recipients.add(delivery.getRenterProfileId());
        }

        return new AnnouncementHistoryItem(
                announcement.getId(),
                announcement.getMessage(),
                announcement.getPriority(),
                channels,
                announcement.getExpiresAt(),
                announcement.getCreatedAt(),
                stats,
                readCount,
                recipients.size()
        );
    }
}
