package com.rentmanager.modules.announcement.infrastructure.persistence.mapper;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AnnouncementPersistenceMapper {

    public AnnouncementJpaEntity toJpaEntity(Announcement announcement) {
        AnnouncementJpaEntity jpa = new AnnouncementJpaEntity(
                announcement.getAuthorId(),
                announcement.getMessage(),
                announcement.getPriority(),
                joinChannels(announcement.getChannels()),
                announcement.getExpiresAt()
        );
        jpa.restoreId(announcement.getId());
        jpa.assignTenantIfUnset(announcement.getTenantId());
        jpa.setVersion(announcement.getVersion());
        return jpa;
    }

    public Announcement toDomain(AnnouncementJpaEntity jpa) {
        return Announcement.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getAuthorId(),
                jpa.getMessage(),
                jpa.getPriority(),
                splitChannels(jpa.getChannels()),
                jpa.getExpiresAt(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt(),
                jpa.getVersion()
        );
    }

    private String joinChannels(Set<AnnouncementChannel> channels) {
        return channels.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private Set<AnnouncementChannel> splitChannels(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of(AnnouncementChannel.IN_APP);
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(AnnouncementChannel::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
