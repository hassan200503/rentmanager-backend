package com.rentmanager.modules.platformsettings.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformBrandingResponse;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.shared.web.PublicCacheControl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public platform identity — no authentication, nothing operational.
 *
 * <p>Whitelisted by the existing {@code /api/v1/public/**} security rule so
 * the landing page, auth surfaces, the favicon resolver and email templates
 * can pull the system logo before (or without) any user session. The
 * logo is now served by this application itself
 * ({@code /api/v1/public/platform/branding/logo}), with a legacy Cloudinary
 * URL still tolerated.</p>
 *
 * <p>This is the single most-requested endpoint in the system: the web app's
 * {@code /icon} route, both mobile apps and every first page load read it. It
 * therefore carries {@link PublicCacheControl#branding()} so the answer is
 * held by the CDN and the browser for five minutes instead of waking the
 * free-tier instance on each visit.</p>
 */
@RestController
@RequestMapping("/api/v1/public/platform/branding")
@RequiredArgsConstructor
public class PlatformBrandingPublicController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlatformBrandingResponse>> getBranding() {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.branding())
                .body(ApiResponse.ok(
                        "Platform branding",
                        platformSettingsService.getBranding()));
    }
}