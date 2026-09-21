package com.rentmanager.shared.web;

import org.springframework.http.CacheControl;

import java.time.Duration;

/**
 * The caching policy for the handful of endpoints that answer the same thing
 * to every visitor.
 *
 * <h2>Why this exists</h2>
 * The API runs on one small always-free instance. Every visit to the landing
 * page and the listings page asked it for the same catalogue and the same
 * branding, and measured from Nairobi a public read cost over a second of the
 * page's time — for an answer that had not changed. A minute of shared caching
 * removes almost all of those calls, and leaves the instance's CPU for the
 * work that is actually per-person.
 *
 * <h2>Why the header has to be set here, in the controller</h2>
 * Spring Security writes {@code Cache-Control: no-cache, no-store} on every
 * response by default, and only stands aside when the response already carries
 * one of the cache headers. So a cacheable endpoint must declare it on its own
 * {@code ResponseEntity}; a filter added after the fact either fights that
 * writer or is silently overridden by it.
 *
 * <h2>What must never use this</h2>
 * Anything a signed-in user asked for, and anything keyed to one person even
 * when it needs no sign-in — a reservation's payment status, for instance.
 * A shared cache holding one person's answer and giving it to the next is the
 * failure mode worth being strict about, so these constants are only for
 * catalogue and branding data.
 */
public final class PublicCacheControl {

    private PublicCacheControl() {
    }

    /**
     * Public catalogue: listings, units, landlord reviews.
     *
     * <p>One minute. Long enough that a page's repeat reads and a burst of
     * visitors collapse into a single origin call, short enough that a newly
     * published listing appears while the landlord is still looking at it.
     * {@code stale-while-revalidate} matters more here than the freshness
     * window: when the free instance is asleep, a visitor gets the previous
     * answer instantly instead of waiting for it to wake.
     */
    public static CacheControl catalogue() {
        return CacheControl.maxAge(Duration.ofSeconds(60))
                .cachePublic()
                .staleWhileRevalidate(Duration.ofMinutes(5));
    }

    /**
     * Platform branding: the name and logo every page and both apps read.
     *
     * <p>Five minutes, matching the brand icon endpoint and the web app's
     * {@code /icon} route, because the owner changes this roughly never and it
     * is fetched on nearly every first page load.
     */
    public static CacheControl branding() {
        return CacheControl.maxAge(Duration.ofMinutes(5))
                .cachePublic()
                .staleWhileRevalidate(Duration.ofDays(1));
    }
}
