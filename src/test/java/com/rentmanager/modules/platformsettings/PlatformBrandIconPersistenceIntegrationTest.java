package com.rentmanager.modules.platformsettings;

import com.rentmanager.modules.platformsettings.infrastructure.persistence.PlatformBrandIconStore;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Postgres (V104) for the platform icon the owner uploads once.
 *
 * The guarantees here are SQL: the upsert keeps exactly one icon, the row id is
 * pinned so "which icon is current?" cannot become ambiguous, and the content
 * type and size are constrained in the schema as well as in the service.
 */
// Rolled back after each test: this suite shares one reusable Postgres
// container, and rows left behind change what other suites see.
@org.springframework.transaction.annotation.Transactional
class PlatformBrandIconPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 9};

    @Autowired private PlatformBrandIconStore store;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void uploadingAgainReplacesTheIconInsteadOfAddingASecondOne() {
        store.save(PNG, "image/png", "owner@example.com");
        assertThat(store.exists()).isTrue();
        assertThat(store.find()).get().satisfies(icon -> {
            assertThat(icon.bytes()).isEqualTo(PNG);
            assertThat(icon.contentType()).isEqualTo("image/png");
            assertThat(icon.updatedAt()).isNotNull();
        });

        store.save(JPEG, "image/jpeg", "owner@example.com");

        assertThat(store.find()).get().satisfies(icon -> {
            assertThat(icon.bytes()).isEqualTo(JPEG);
            assertThat(icon.contentType()).isEqualTo("image/jpeg");
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_brand_icon", Integer.class)).isEqualTo(1);
    }

    @Test
    void removingItLeavesNothingBehind() {
        store.save(PNG, "image/png", "owner@example.com");
        store.delete();

        assertThat(store.exists()).isFalse();
        assertThat(store.find()).isEmpty();
    }

    @Test
    void theDatabaseRefusesASecondIconRow() {
        store.save(PNG, "image/png", "owner@example.com");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_brand_icon (id, content_type, bytes, byte_size, updated_by)
                VALUES (2, 'image/png', ?, 3, 'someone')
                """, (Object) PNG))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // One constraint per test: after a violation Postgres aborts the
    // transaction, so a second statement in the same test fails for that
    // reason instead of its own constraint — which is what made this test
    // fail the first time it ran.

    @Test
    void theDatabaseRefusesAnSvgEvenIfTheServiceRegressed() {
        // SVG can carry script and this file is served from our own origin.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_brand_icon (id, content_type, bytes, byte_size, updated_by)
                VALUES (1, 'image/svg+xml', ?, 3, 'someone')
                """, (Object) PNG))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesAnOversizedIconEvenIfTheServiceRegressed() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO platform_brand_icon (id, content_type, bytes, byte_size, updated_by)
                VALUES (1, 'image/png', ?, 524289, 'someone')
                """, (Object) PNG))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
