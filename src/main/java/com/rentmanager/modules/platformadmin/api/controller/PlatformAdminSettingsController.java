package com.rentmanager.modules.platformadmin.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformsettings.api.dto.request.UpdatePlatformSettingsRequest;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformSettingsResponse;
import com.rentmanager.modules.platformsettings.application.service.PlatformSettingsService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Platform-wide owner configuration surface.
 *
 * <p>Reads are open to any platform operator (OWNER/ADMIN); writes are
 * restricted to ROLE_PLATFORM_OWNER — platform settings govern money
 * behaviour (grace periods, payout retries, revenue identifiers), so a
 * staff admin must never be able to change them.</p>
 *
 * <p>The system logo is configurable only through the dedicated multi-part
 * endpoints — it is never part of the generic settings payload.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
public class PlatformAdminSettingsController {

    private final PlatformSettingsService platformSettingsService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.ok("Platform settings", platformSettingsService.get()));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> updateSettings(
            @Valid @RequestBody UpdatePlatformSettingsRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform settings updated",
                platformSettingsService.update(request, resolveActor(authentication))));
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> uploadLogo(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform logo updated",
                platformSettingsService.uploadLogo(file, resolveActor(authentication))));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<PlatformSettingsResponse>> removeLogo(
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform logo removed",
                platformSettingsService.removeLogo(resolveActor(authentication))));
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUserId() != null ? user.getUserId().toString() : user.getEmail();
        }
        return "platform-owner";
    }
}