package com.rentmanager.modules.notification.push.infrastructure;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "push_devices")
public class PushDeviceJpaEntity extends BaseEntity {

    @Column(name = "clerk_user_id", nullable = false)
    private String clerkUserId;

    @Column(name = "push_token", nullable = false, updatable = false)
    private String pushToken;

    @Column(name = "platform", nullable = false)
    @Enumerated(EnumType.STRING)
    private PushPlatform platform;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
