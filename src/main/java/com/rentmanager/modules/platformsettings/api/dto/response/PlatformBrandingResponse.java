package com.rentmanager.modules.platformsettings.api.dto.response;

import java.time.Instant;

/**
 * Minimal, unauthenticated platform identity served by
 * {@code GET /api/v1/public/platform/branding} to every chrome surface
 * (console, landlord/renter shells, landing page, favicon resolver, email
 * templates).
 *
 * <p>Deliberately contains NO operational configuration — only the fields a
 * brand mark needs. Support contact is included because it is meant to be
 * public (shown on platform housekeeping pages).</p>
 */
public record PlatformBrandingResponse(
        String platformName,
        String logoUrl,
        String environment,
        String supportEmail,
        String supportPhone,
        Instant updatedAt
) {
}