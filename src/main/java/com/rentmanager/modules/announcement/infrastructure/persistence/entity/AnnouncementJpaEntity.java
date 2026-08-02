package com.rentmanager.modules.announcement.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "announcements")
public class AnnouncementJpaEntity extends BaseTenantEntity {

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private AnnouncementPriority priority;

    /** Comma-separated channel names, e.g. "IN_APP,SMS,EMAIL,WHATSAPP". */
    @Column(name = "channels", nullable = false, length = 100)
    private String channels;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
