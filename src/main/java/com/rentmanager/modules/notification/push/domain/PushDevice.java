package com.rentmanager.modules.notification.push.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One app installation's push token, owned by one person (Clerk user).
 *
 * The only transitions are {@link #claimFor} (register / re-register, which
 * may move ownership to whoever is now signed in on the device) and
 * {@link #revoke} (sign-out, or the provider reporting the token dead).
 */
@Getter
public class PushDevice {

    /** Expo push token shapes: ExponentPushToken[...] and ExpoPushToken[...]. */
    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[A-Za-z0-9_\\-]{10,200}]$");

    private final UUID id;
    private String clerkUserId;
    private final String pushToken;
    private PushPlatform platform;
    private String appVersion;
    private Instant revokedAt;
    private Instant lastSeenAt;
    private final Instant createdAt;
    private Long version;

    private PushDevice(UUID id, String clerkUserId, String pushToken, PushPlatform platform, String appVersion,
                       Instant revokedAt, Instant lastSeenAt, Instant createdAt, Long version) {
        this.id = id;
        this.clerkUserId = clerkUserId;
        this.pushToken = pushToken;
        this.platform = platform;
        this.appVersion = appVersion;
        this.revokedAt = revokedAt;
        this.lastSeenAt = lastSeenAt;
        this.createdAt = createdAt;
        this.version = version;
    }

    public static boolean isValidToken(String token) {
        return token != null && EXPO_TOKEN.matcher(token).matches();
    }

    public static PushDevice register(String clerkUserId, String pushToken, PushPlatform platform, String appVersion) {
        requireOwner(clerkUserId);
        if (!isValidToken(pushToken)) {
            throw new IllegalArgumentException("Invalid push token");
        }
        if (platform == null) {
            throw new IllegalArgumentException("Platform is required");
        }
        Instant now = Instant.now();
        return new PushDevice(UUID.randomUUID(), clerkUserId, pushToken, platform, truncate(appVersion),
                null, now, now, 0L);
    }

    public static PushDevice rehydrate(UUID id, String clerkUserId, String pushToken, PushPlatform platform,
                                       String appVersion, Instant revokedAt, Instant lastSeenAt,
                                       Instant createdAt, Long version) {
        return new PushDevice(id, clerkUserId, pushToken, platform, appVersion, revokedAt, lastSeenAt,
                createdAt, version);
    }

    /**
     * Re-registration by whoever is signed in on the device now. If that is a
     * different person, ownership moves to them — the previous owner's
     * notifications must stop reaching a phone they no longer use.
     */
    public void claimFor(String clerkUserId, PushPlatform platform, String appVersion) {
        requireOwner(clerkUserId);
        this.clerkUserId = clerkUserId;
        if (platform != null) {
            this.platform = platform;
        }
        this.appVersion = truncate(appVersion);
        this.revokedAt = null;
        this.lastSeenAt = Instant.now();
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean isOwnedBy(String clerkUserId) {
        return clerkUserId != null && clerkUserId.equals(this.clerkUserId);
    }

    private static void requireOwner(String clerkUserId) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("Owner is required");
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 64 ? value : value.substring(0, 64);
    }
}
