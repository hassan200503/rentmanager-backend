package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.api.routes.UnitRoutes;
import com.rentmanager.modules.unit.application.command.service.UnitMediaCommandService;
import com.rentmanager.modules.unit.application.dto.request.ReorderUnitMediaRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitMediaRequest;
import com.rentmanager.modules.unit.application.query.service.UnitMediaQueryService;
import com.rentmanager.shared.dto.MediaUploadResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import com.rentmanager.shared.service.MediaUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Previously carried no {@code @PreAuthorize} at all — every method required
 * only a valid JWT, no role check. Tenant scoping is correctly enforced one
 * layer down (every {@code MediaUploadService}/{@code UnitMediaCommandService}
 * call is scoped by {@code tenantId}), but a renter's JWT carries the
 * landlord's {@code tenantId} too ({@code ClerkJwtAuthenticationConverter} sets
 * {@code TenantContext} from the JWT regardless of role), so a renter could
 * reach every method here for units belonging to the landlord they rent from —
 * upload arbitrary files, delete the landlord's real photos, or reorder /
 * rename captions. Reads are OWNER/MANAGER/STAFF, matching
 * {@code UnitQueryController}; writes are OWNER/MANAGER only, matching
 * {@code PropertyCommandController}'s property-mutation gate. Whether STAFF
 * should also be able to upload/manage media (a caretaker adding photos) is a
 * genuine open product question, not decided here — see
 * {@code docs/ai/TECHNICAL_DEBT.md} TD-112.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(UnitRoutes.BASE)
public class UnitMediaController {

    private final MediaUploadService mediaUploadService;
    private final UnitMediaQueryService unitMediaQueryService;
    private final UnitMediaCommandService unitMediaCommandService;

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    @GetMapping("/{unitId}/media")
    public ApiResponse<List<MediaUploadResponse>> getUnitMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId
    ) {
        return ApiResponse.ok(
                "Unit media retrieved successfully",
                unitMediaQueryService.getUnitMedia(
                        requireTenantId(user),
                        unitId
                )
        );
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PostMapping(
            value = "/{unitId}/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<MediaUploadResponse> uploadUnitMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary
    ) {
        return ApiResponse.ok(
                "Unit media uploaded successfully",
                mediaUploadService.uploadUnitMedia(
                        requireTenantId(user),
                        unitId,
                        file,
                        primary
                )
        );
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @DeleteMapping("/{unitId}/media/{mediaId}")
    public ApiResponse<Void> deleteUnitMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId,
            @PathVariable UUID mediaId
    ) {
        mediaUploadService.deleteUnitMedia(
                requireTenantId(user),
                mediaId
        );

        return ApiResponse.ok(
                "Unit media deleted successfully",
                null
        );
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PutMapping("/{unitId}/media/{mediaId}/primary")
    public ApiResponse<Void> setPrimaryMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId,
            @PathVariable UUID mediaId
    ) {
        unitMediaCommandService.setPrimaryMedia(
                requireTenantId(user),
                unitId,
                mediaId
        );

        return ApiResponse.ok(
                "Primary media updated successfully",
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


    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PatchMapping("/{unitId}/media/{mediaId}")
    public ApiResponse<Void> updateCaption(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId,
            @PathVariable UUID mediaId,
            @RequestBody UpdateUnitMediaRequest request
    ) {

        unitMediaCommandService.updateCaption(
                requireTenantId(user),
                unitId,
                mediaId,
                request.caption()
        );

        return ApiResponse.ok(
                "Unit media updated successfully",
                null
        );
    }


    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PutMapping("/{unitId}/media/reorder")
    public ApiResponse<Void> reorderMedia(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID unitId,
            @RequestBody ReorderUnitMediaRequest request
    ) {

        unitMediaCommandService.reorderMedia(
                requireTenantId(user),
                unitId,
                request.mediaIds()
        );

        return ApiResponse.ok(
                "Unit media reordered successfully",
                null
        );
    }
}