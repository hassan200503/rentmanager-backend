package com.rentmanager.modules.identity.account;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * The signed-in person deletes their own account. There is no path parameter
 * and no body: the only account this can ever act on is the token's subject.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account/deletion")
@PreAuthorize("isAuthenticated()")
public class AccountDeletionController {

    private final AccountDeletionService service;

    public record AccountDeletionResponse(String status, String message) {}

    public record AccountDeletionStatusResponse(String status, Instant requestedAt) {}

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDeletionResponse>> requestDeletion(Authentication authentication) {
        AccountDeletionService.Result result = service.requestDeletion(subject(authentication));
        return ResponseEntity.ok(ApiResponse.ok(result.message(),
                new AccountDeletionResponse(result.outcome().name(), result.message())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountDeletionStatusResponse>> latest(Authentication authentication) {
        AccountDeletionStatusResponse response = service.latest(subject(authentication))
                .map(l -> new AccountDeletionStatusResponse(l.status(), l.requestedAt()))
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private static String subject(Authentication authentication) {
        if (authentication instanceof ClerkAuthenticationToken token
                && token.getCredentials() instanceof Jwt jwt
                && jwt.getSubject() != null) {
            return jwt.getSubject();
        }
        throw new SecurityException("Requires a Clerk session");
    }
}
