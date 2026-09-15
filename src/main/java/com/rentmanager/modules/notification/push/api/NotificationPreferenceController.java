package com.rentmanager.modules.notification.push.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.notification.push.application.NotificationPreferenceService;
import com.rentmanager.modules.notification.push.domain.PushCategory;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The signed-in person's own push preferences. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-preferences")
@PreAuthorize("isAuthenticated()")
public class NotificationPreferenceController {

    private final NotificationPreferenceService service;

    public record NotificationPreferencesResponse(boolean rentPayments, boolean maintenance) {
        static NotificationPreferencesResponse from(Map<PushCategory, Boolean> prefs) {
            return new NotificationPreferencesResponse(
                    prefs.getOrDefault(PushCategory.RENT_PAYMENTS, true),
                    prefs.getOrDefault(PushCategory.MAINTENANCE, true));
        }
    }

    public record UpdateNotificationPreferencesRequest(@NotNull Boolean rentPayments, @NotNull Boolean maintenance) {}

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> get(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(
                NotificationPreferencesResponse.from(service.get(subject(authentication)))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> update(
            Authentication authentication,
            @jakarta.validation.Valid @RequestBody UpdateNotificationPreferencesRequest request
    ) {
        Map<PushCategory, Boolean> updated = service.update(subject(authentication), Map.of(
                PushCategory.RENT_PAYMENTS, request.rentPayments(),
                PushCategory.MAINTENANCE, request.maintenance()));
        return ResponseEntity.ok(ApiResponse.ok("Preferences saved", NotificationPreferencesResponse.from(updated)));
    }

    static String subject(Authentication authentication) {
        if (authentication instanceof ClerkAuthenticationToken token
                && token.getCredentials() instanceof Jwt jwt
                && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        throw new SecurityException("Requires a Clerk session");
    }
}
