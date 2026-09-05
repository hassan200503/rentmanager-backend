package com.rentmanager.modules.integration.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.integration.api.dto.IntegrationDtos;
import com.rentmanager.modules.integration.application.IntegrationAdminService;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Integrations Control Plane — the single Owner-facing surface for every
 * external provider the platform depends on.
 *
 * Reads are open to platform operators (OWNER/ADMIN) and always return
 * masked credentials. Every write (save / activate / test) is restricted
 * to ROLE_PLATFORM_OWNER and is appended to the integration audit trail.
 *
 * Guard rails enforced here:
 *  - activate(PRODUCTION) is refused unless the config was VERIFIED by a
 *    real Test Connection since the last credential change
 *  - requests raced against a sibling environment are handled by the
 *    service (activating one environment deactivates the other)
 */
@RestController
@RequestMapping("/api/v1/admin/integrations")
@RequiredArgsConstructor
public class PlatformIntegrationsController {

    private final IntegrationAdminService integrationAdminService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<IntegrationDtos.ProviderView>>> listIntegrations() {
        return ResponseEntity.ok(ApiResponse.ok("Integration providers", integrationAdminService.list()));
    }

    @GetMapping("/{provider}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<IntegrationDtos.ProviderView>> getIntegration(
            @PathVariable String provider
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Integration provider", integrationAdminService.get(provider)));
    }

    @GetMapping("/{provider}/audit")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<IntegrationDtos.AuditEntryView>>> integrationAudit(
            @PathVariable String provider,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Integration audit trail", integrationAdminService.audit(provider, limit)));
    }

    @PutMapping("/{provider}/{environment}")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<IntegrationDtos.ProviderView>> saveCredentials(
            @PathVariable String provider,
            @PathVariable IntegrationEnvironment environment,
            @Valid @RequestBody IntegrationDtos.UpdateCredentialsRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Integration credentials saved",
                integrationAdminService.update(
                        provider,
                        environment,
                        request.credentials(),
                        resolveActor(authentication),
                        httpRequest.getRemoteAddr())));
    }

    @PostMapping("/{provider}/{environment}/activate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<IntegrationDtos.ActivateResultView>> activate(
            @PathVariable String provider,
            @PathVariable IntegrationEnvironment environment,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Integration environment activated",
                integrationAdminService.activate(
                        provider,
                        environment,
                        resolveActor(authentication),
                        httpRequest.getRemoteAddr())));
    }

    @PostMapping("/{provider}/{environment}/test")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<IntegrationDtos.TestConnectionView>> testConnection(
            @PathVariable String provider,
            @PathVariable IntegrationEnvironment environment,
            @RequestBody(required = false) IntegrationDtos.TestConnectionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Test connection result",
                integrationAdminService.test(
                        provider,
                        environment,
                        request == null ? null : request.target(),
                        resolveActor(authentication),
                        httpRequest.getRemoteAddr())));
    }

    @PostMapping("/roll-to-production")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<IntegrationDtos.RolloutView>> rollToProduction(
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Production rollout attempted",
                integrationAdminService.rollToProduction(
                        resolveActor(authentication),
                        httpRequest.getRemoteAddr())));
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof com.rentmanager.shared.security.principal.AuthenticatedUser user) {
            return user.getUserId() != null ? user.getUserId().toString() : user.getEmail();
        }
        return "platform-owner";
    }
}