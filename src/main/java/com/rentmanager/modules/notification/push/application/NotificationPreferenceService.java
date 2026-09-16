package com.rentmanager.modules.notification.push.application;

import com.rentmanager.modules.notification.push.domain.PushCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Push preferences per person. Identity always comes from the verified token
 * (the controller passes the Clerk subject); there is no way to read or change
 * someone else's preferences.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<PushCategory, Boolean> get(String clerkUserId) {
        Map<PushCategory, Boolean> prefs = new EnumMap<>(PushCategory.class);
        for (PushCategory c : PushCategory.values()) {
            prefs.put(c, true);
        }
        jdbc.query(
                "SELECT category, push_enabled FROM notification_preferences WHERE clerk_user_id = ?",
                rs -> {
                    try {
                        prefs.put(PushCategory.valueOf(rs.getString(1)), rs.getBoolean(2));
                    } catch (IllegalArgumentException ignored) {
                        // A category removed from the enum; ignore the stale row.
                    }
                },
                clerkUserId);
        return prefs;
    }

    @Transactional
    public Map<PushCategory, Boolean> update(String clerkUserId, Map<PushCategory, Boolean> changes) {
        Timestamp now = Timestamp.from(Instant.now());
        changes.forEach((category, enabled) -> {
            if (category == null || enabled == null) {
                return;
            }
            jdbc.update("""
                    INSERT INTO notification_preferences (clerk_user_id, category, push_enabled, updated_at)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (clerk_user_id, category)
                    DO UPDATE SET push_enabled = EXCLUDED.push_enabled, updated_at = EXCLUDED.updated_at
                    """, clerkUserId, category.name(), enabled, now);
        });
        return get(clerkUserId);
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(String clerkUserId, PushCategory category) {
        Boolean enabled = jdbc.query(
                "SELECT push_enabled FROM notification_preferences WHERE clerk_user_id = ? AND category = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                clerkUserId, category.name());
        return enabled == null || enabled;
    }

    @Transactional
    public void deleteAll(String clerkUserId) {
        jdbc.update("DELETE FROM notification_preferences WHERE clerk_user_id = ?", clerkUserId);
    }
}
