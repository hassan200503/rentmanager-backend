package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.dto.response.PropertyMediaResponse;
import com.rentmanager.modules.property.application.query.service.PropertyMediaQueryService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(PropertyRoutes.BASE)
public class PropertyMediaQueryController {

    private final PropertyMediaQueryService propertyMediaQueryService;

    @GetMapping("/{propertyId}/media")
    public ApiResponse<List<PropertyMediaResponse>> getPropertyMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        return ApiResponse.ok(
                "Property media retrieved successfully",
                propertyMediaQueryService.getPropertyMedia(
                        requireTenantId(user),
                        propertyId
                )
        );
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();

        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant associated with this user. Please complete onboarding."
            );
        }

        return tenantId;
    }
}