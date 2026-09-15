package com.rentmanager.modules.user.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.user.application.dto.response.SessionAccessResponse;
import com.rentmanager.modules.user.application.dto.response.UserResponse;
import com.rentmanager.modules.user.application.query.access.SessionAccessResolver;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.rentmanager.modules.user.application.query.service.UserQueryService;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * SECURITY NOTE (consistent with UnitQueryController / UnitCommandController):
 * userId and tenantId are NEVER accepted from client-supplied parameters here.
 * Both are derived exclusively from TenantContext, populated server-side by
 * ClerkJwtAuthenticationConverter from the verified Clerk JWT. Do not accept
 * an @RequestParam or @RequestHeader override for either value — doing so
 * would allow any authenticated user to query another tenant's user list,
 * or impersonate another user's "me" lookup.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserQueryController {

    private final UserQueryService userQueryService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UUID userId = TenantContext.getUserId();

        UserResponse response = userQueryService.getCurrentUser(userId);

        return ResponseEntity.ok(
                ApiResponse.ok("Current user retrieved successfully", response)
        );
    }

    /**
     * The experiences this session is authorised for, from the same
     * authorities method security evaluates. Clients route on this rather
     * than on JWT claims or Clerk metadata. A hint for UI only — every
     * endpoint still enforces its own gate.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/access")
    public ResponseEntity<ApiResponse<SessionAccessResponse>> getCurrentAccess(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        SessionAccessResponse response = SessionAccessResolver.resolve(
                user.getUserId(), user.getTenantId(), user.getAuthorities());
        return ResponseEntity.ok(ApiResponse.ok("Session access resolved", response));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getTenantUsers(
            Pageable pageable
    ) {
        UUID tenantId = TenantContext.getTenantId();

        Page<UserResponse> response = userQueryService.getByTenant(tenantId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Tenant users retrieved successfully", response)
        );
    }
}
