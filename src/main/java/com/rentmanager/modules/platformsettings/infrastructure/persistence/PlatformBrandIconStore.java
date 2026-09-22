package com.rentmanager.modules.platformsettings.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * The platform's brand icon, held in our own database (V104).
 *
 * JDBC rather than JPA: one row, read on nearly every page load, written by a
 * multipart endpoint. There is no aggregate here to model.
 */
@Repository
@RequiredArgsConstructor
public class PlatformBrandIconStore {

    /** What the owner uploaded, and when — the timestamp is the ETag. */
    public record BrandIcon(byte[] bytes, String contentType, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;

    public Optional<BrandIcon> find() {
        return jdbc.query(
                "SELECT bytes, content_type, updated_at FROM platform_brand_icon WHERE id = 1",
                rs -> rs.next()
                        ? Optional.of(new BrandIcon(
                                rs.getBytes("bytes"),
                                rs.getString("content_type"),
                                rs.getTimestamp("updated_at").toInstant()))
                        : Optional.empty());
    }

    /** True when an icon exists, without reading the bytes. */
    public boolean exists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_brand_icon WHERE id = 1", Integer.class);
        return count != null && count > 0;
    }

    /**
     * When the current icon was stored, without reading the bytes.
     *
     * <p>This is what makes the public logo URL change when the owner uploads a
     * new icon. Serving it from a fixed path meant a replacement was invisible
     * to every cache between here and the browser: same URL, so nothing had any
     * reason to ask again.
     */
    public Optional<Instant> updatedAt() {
        return jdbc.query(
                "SELECT updated_at FROM platform_brand_icon WHERE id = 1",
                rs -> rs.next()
                        ? Optional.of(rs.getTimestamp("updated_at").toInstant())
                        : Optional.empty());
    }

    /** Replaces the icon. Upsert, because there is exactly one. */
    public void save(byte[] bytes, String contentType, String actor) {
        jdbc.update("""
                INSERT INTO platform_brand_icon (id, content_type, bytes, byte_size, updated_by, updated_at)
                VALUES (1, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    content_type = EXCLUDED.content_type,
                    bytes        = EXCLUDED.bytes,
                    byte_size    = EXCLUDED.byte_size,
                    updated_by   = EXCLUDED.updated_by,
                    updated_at   = now()
                """, contentType, bytes, bytes.length, actor);
    }

    public void delete() {
        jdbc.update("DELETE FROM platform_brand_icon WHERE id = 1");
    }
}
