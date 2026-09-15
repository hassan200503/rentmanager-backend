package com.rentmanager.modules.notification.push.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.notification.push.application.PushDeviceService;
import com.rentmanager.modules.notification.push.domain.PushPlatform;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device registration for mobile push. The owner is always the Clerk subject
 * of the verified token — never a body field — so a caller can register or
 * unregister only their own devices.
 *
 * Open to any authenticated user (landlord member, renter, or someone still
 * onboarding): a device belongs to a person, and which notifications they
 * receive is decided by the listeners from the organisation an event belongs
 * to, not by this endpoint.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices/push")
@PreAuthorize("isAuthenticated()")
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    public record RegisterPushDeviceRequest(
            @NotBlank @Size(max = 255) String token,
            @NotNull PushPlatform platform,
            @Size(max = 64) String appVersion
    ) {}

    public record UnregisterPushDeviceRequest(@NotBlank @Size(max = 255) String token) {}

    public record PushDeviceRegistrationResponse(boolean registered) {}

    @PostMapping
    public ResponseEntity<ApiResponse<PushDeviceRegistrationResponse>> register(
            Authentication authentication,
            @Valid @RequestBody RegisterPushDeviceRequest request
    ) {
        pushDeviceService.register(clerkSubject(authentication), request.token(), request.platform(),
                request.appVersion());
        return ResponseEntity.ok(ApiResponse.ok("Device registered", new PushDeviceRegistrationResponse(true)));
    }

    /**
     * POST rather than DELETE-with-body, and deliberately not token-in-path:
     * a push token in a URL ends up in access logs.
     */
    @PostMapping("/unregister")
    public ResponseEntity<ApiResponse<PushDeviceRegistrationResponse>> unregister(
            Authentication authentication,
            @Valid @RequestBody UnregisterPushDeviceRequest request
    ) {
        pushDeviceService.unregister(clerkSubject(authentication), request.token());
        return ResponseEntity.ok(ApiResponse.ok("Device unregistered", new PushDeviceRegistrationResponse(false)));
    }

    private static String clerkSubject(Authentication authentication) {
        if (authentication instanceof ClerkAuthenticationToken token
                && token.getCredentials() instanceof Jwt jwt
                && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        throw new SecurityException("Push registration requires a Clerk session");
    }
}
