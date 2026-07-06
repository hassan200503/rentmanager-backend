package com.rentmanager.modules.user.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.user.application.dto.response.UserResponse;
import com.rentmanager.modules.user.application.query.service.UserQueryService;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser() {
        UUID userId = TenantContext.getUserId();

        UserResponse response = userQueryService.getCurrentUser(userId);

        return ResponseEntity.ok(
                ApiResponse.ok("Current user retrieved successfully", response)
        );
    }

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