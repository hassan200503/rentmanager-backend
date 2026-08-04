package com.rentmanager.modules.property.api.controller;

import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.dto.response.PropertyTypeMetadataResponse;
import com.rentmanager.modules.property.application.query.service.PropertyQueryService;
import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(PropertyRoutes.BASE)
public class PropertyQueryController {

    private final PropertyQueryService propertyQueryService;

    @GetMapping("/{propertyId}")
    public ResponseEntity<ApiResponse<PropertyResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        PropertyResponse response =
                propertyQueryService.getById(requireTenantId(user), propertyId);

        return ResponseEntity.ok(
                ApiResponse.ok("Property retrieved successfully", response)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PropertyResponse>>> getAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable
    ) {
        Page<PropertyResponse> response =
                propertyQueryService.getAll(requireTenantId(user), pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Properties retrieved successfully", response)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PropertyResponse>>> search(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        Page<PropertyResponse> response =
                propertyQueryService.search(requireTenantId(user), keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Property search completed successfully", response)
        );
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getByOwner(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID ownerId
    ) {
        List<PropertyResponse> response =
                propertyQueryService.getByOwner(requireTenantId(user), ownerId);

        return ResponseEntity.ok(
                ApiResponse.ok("Owner properties retrieved successfully", response)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getByStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String status
    ) {
        List<PropertyResponse> response =
                propertyQueryService.getByStatus(requireTenantId(user), status);

        return ResponseEntity.ok(
                ApiResponse.ok("Properties retrieved successfully", response)
        );
    }

    /**
     * Taxonomy metadata for the property creation form: every property type
     * with its auto-derived premises classification, plus the full premises
     * override list. Authentication required; deliberately not tenant-scoped
     * (the taxonomy is global). Must be declared before {@code /{propertyId}}
     * resolution — an exact literal path wins in Spring routing regardless.
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<PropertyTypeMetadataResponse>> getPropertyTypes(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        if (user == null) {
            throw new IllegalStateException("Authentication required");
        }
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Property taxonomy retrieved successfully",
                        propertyQueryService.getPropertyTypes()
                )
        );
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}