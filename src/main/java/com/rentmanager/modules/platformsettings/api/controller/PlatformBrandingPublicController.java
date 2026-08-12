package com.rentmanager.modules.platformsettings.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformBrandingResponse;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
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
 * Cloudinary URL the logo points to is already public CDN content.</p>
 */
@RestController
@RequestMapping("/api/v1/public/platform/branding")
@RequiredArgsConstructor
public class PlatformBrandingPublicController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<PlatformBrandingResponse>> getBranding() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform branding",
                platformSettingsService.getBranding()));
    }
}