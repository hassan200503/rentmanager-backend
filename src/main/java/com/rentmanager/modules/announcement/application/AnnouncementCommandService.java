package com.rentmanager.modules.announcement.application;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementCommandService {

    private final AnnouncementRepository announcementRepository;
    private final DomainEventPublisher eventPublisher;

    /**
     * Persists the announcement (the only thing the HTTP request does to
     * the fan-out) and publishes the event. The broadcast listener enqueues
     * the per-recipient delivery rows AFTER commit - nothing downstream can
     * roll this write back, and the request never iterates recipients.
     */
    @Transactional
    public Announcement create(
            UUID tenantId,
            UUID authorId,
            String message,
            AnnouncementPriority priority,
            Set<AnnouncementChannel> channels,
            Instant expiresAt
    ) {
        Announcement announcement = Announcement.create(
                tenantId, authorId, message, priority, channels, expiresAt, UUID.randomUUID().toString());

        // Pull the events BEFORE save: the repository adapter persists and
        // returns a REHYDRATED instance (mapper.toDomain), so events pulled
        // from the returned object would always be empty and the broadcast
        // fan-out would never fire.
        List<DomainEvent> events = announcement.pullDomainEvents();
        announcement = announcementRepository.save(announcement);
        eventPublisher.publishAll(events);

        log.info("Announcement created: id={} tenantId={} channels={} priority={}",
                announcement.getId(), tenantId, announcement.getChannels(), priority);

        return announcement;
    }
}
