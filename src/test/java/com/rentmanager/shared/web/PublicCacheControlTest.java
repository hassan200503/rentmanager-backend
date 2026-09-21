package com.rentmanager.shared.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exact header the CDN and every browser will act on.
 *
 * <p>These assertions look pedantic, and that is the point: the difference
 * between {@code public} and {@code private} here is the difference between
 * a shared cache being allowed to serve this answer to the next visitor and
 * not. A future edit that adds a person's data to a catalogue response has to
 * change the policy too, and a test naming the directives makes that visible
 * rather than silent.
 */
class PublicCacheControlTest {

    @Test
    void catalogueIsSharedCacheable_forOneMinute_withAStaleFallback() {
        String header = PublicCacheControl.catalogue().getHeaderValue();

        assertThat(header).contains("max-age=60");
        assertThat(header).contains("public");
        // When the free-tier instance is asleep, the previous answer is served
        // instantly rather than the visitor waiting for it to wake.
        assertThat(header).contains("stale-while-revalidate=300");
        assertThat(header).doesNotContain("no-store");
        assertThat(header).doesNotContain("private");
    }

    @Test
    void brandingIsSharedCacheable_forFiveMinutes_matchingTheIconRoutes() {
        String header = PublicCacheControl.branding().getHeaderValue();

        assertThat(header).contains("max-age=300");
        assertThat(header).contains("public");
        assertThat(header).contains("stale-while-revalidate=86400");
        assertThat(header).doesNotContain("private");
    }
}
