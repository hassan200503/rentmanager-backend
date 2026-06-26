package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.command.service.PropertyMediaCommandService;
import com.rentmanager.modules.property.application.dto.request.ReorderPropertyMediaBatchRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyMediaRequest;
import com.rentmanager.shared.dto.MediaUploadResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import com.rentmanager.shared.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(PropertyRoutes.BASE)
public class PropertyMediaController {

    private final MediaUploadService mediaUploadService;
    private final PropertyMediaCommandService propertyMediaCommandService;

    @PostMapping(
            value = "/{propertyId}/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<MediaUploadResponse> uploadPropertyMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary
    ) {
        return ApiResponse.ok(
                "Property media uploaded successfully",
                mediaUploadService.uploadPropertyMedia(
                        requireTenantId(user),
                        propertyId,
                        file,
                        primary
                )
        );
    }

    @DeleteMapping("/{propertyId}/media/{mediaId}")
    public ApiResponse<Void> deletePropertyMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @PathVariable UUID mediaId
    ) {
        mediaUploadService.deletePropertyMedia(
                requireTenantId(user),
                mediaId
        );

        return ApiResponse.ok(
                "Property media deleted successfully",
                null
        );
    }

    @PutMapping("/{propertyId}/media/{mediaId}/primary")
    public ApiResponse<Void> setPrimaryMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @PathVariable UUID mediaId
    ) {
        propertyMediaCommandService.setPrimaryMedia(
                requireTenantId(user),
                propertyId,
                mediaId
        );

        return ApiResponse.ok(
                "Primary media updated successfully",
                null
        );
    }

    @PatchMapping("/{propertyId}/media/{mediaId}")
    public ApiResponse<Void> updateCaption(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @PathVariable UUID mediaId,
            @RequestBody UpdatePropertyMediaRequest request
    ) {
        propertyMediaCommandService.updateCaption(
                requireTenantId(user),
                propertyId,
                mediaId,
                request.caption()
        );

        return ApiResponse.ok(
                "Property media updated successfully",
                null
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



    @PatchMapping("/{propertyId}/media/reorder")
    public ApiResponse<Void> reorderMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @RequestBody ReorderPropertyMediaBatchRequest request
    ) {

        propertyMediaCommandService.reorderMedia(
                requireTenantId(user),
                propertyId,
                request.items()
        );

        return ApiResponse.ok(
                "Property media reordered successfully",
                null
        );
    }
}