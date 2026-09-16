package com.rentmanager.modules.identity.account;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Append-only record of deletion requests (V101). */
@Component
@RequiredArgsConstructor
public class AccountDeletionRequestStore {

    public record Latest(String status, Instant requestedAt) {}

    private final JdbcTemplate jdbc;

    public void record(String clerkUserId, String source, String status, String detail) {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO account_deletion_requests (id, clerk_user_id, source, status, detail, requested_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), clerkUserId, source, status,
                detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail),
                Timestamp.from(now),
                "COMPLETED".equals(status) ? Timestamp.from(now) : null);
    }

    public Optional<Latest> latest(String clerkUserId) {
        return jdbc.query("""
                SELECT status, requested_at FROM account_deletion_requests
                WHERE clerk_user_id = ? ORDER BY requested_at DESC LIMIT 1
                """,
                rs -> rs.next() ? Optional.of(new Latest(rs.getString(1), rs.getTimestamp(2).toInstant())) : Optional.empty(),
                clerkUserId);
    }
}
