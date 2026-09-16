package com.rentmanager.modules.maintenance.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.maintenance.application.attachment.MaintenanceAttachmentService;
import com.rentmanager.modules.maintenance.application.attachment.MaintenanceAttachmentService.AttachmentContent;
import com.rentmanager.modules.maintenance.application.attachment.MaintenanceAttachmentService.AttachmentView;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Photo endpoints for maintenance requests, landlord side and renter side.
 * Organisation and renter identity always come from the verified token; ids
 * in the path only select within what the caller already owns.
 *
 * Content responses are {@code private, no-store}: a photo of someone's home
 * must not sit in a shared or on-disk HTTP cache.
 */
@RestController
@RequiredArgsConstructor
public class MaintenanceAttachmentController {

    private final MaintenanceAttachmentService service;

    // ── Landlord ────────────────────────────────────────────────────────────

    @PostMapping(value = "/api/v1/maintenance/{requestId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<AttachmentView>> landlordUpload(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID requestId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Photo attached",
                service.addAsLandlord(user.getTenantId(), requestId, bytes(file))));
    }

    @GetMapping("/api/v1/maintenance/{requestId}/attachments")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<AttachmentView>>> landlordList(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID requestId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.listAsLandlord(user.getTenantId(), requestId)));
    }

    @GetMapping("/api/v1/maintenance/{requestId}/attachments/{attachmentId}/content")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<byte[]> landlordContent(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID requestId,
            @PathVariable UUID attachmentId
    ) {
        return image(service.contentAsLandlord(user.getTenantId(), requestId, attachmentId));
    }

    // ── Renter ──────────────────────────────────────────────────────────────

    @PostMapping(value = "/api/v1/tenant-portal/maintenance/{requestId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_TENANT')")
    public ResponseEntity<ApiResponse<AttachmentView>> renterUpload(
            Authentication authentication,
            @PathVariable UUID requestId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Photo attached",
                service.addAsRenter(subject(authentication), requestId, bytes(file))));
    }

    @GetMapping("/api/v1/tenant-portal/maintenance/{requestId}/attachments")
    @PreAuthorize("hasAuthority('ROLE_TENANT')")
    public ResponseEntity<ApiResponse<List<AttachmentView>>> renterList(
            Authentication authentication,
            @PathVariable UUID requestId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(service.listAsRenter(subject(authentication), requestId)));
    }

    @GetMapping("/api/v1/tenant-portal/maintenance/{requestId}/attachments/{attachmentId}/content")
    @PreAuthorize("hasAuthority('ROLE_TENANT')")
    public ResponseEntity<byte[]> renterContent(
            Authentication authentication,
            @PathVariable UUID requestId,
            @PathVariable UUID attachmentId
    ) {
        return image(service.contentAsRenter(subject(authentication), requestId, attachmentId));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Choose a photo to attach.", ErrorCode.VALIDATION_ERROR);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("The photo couldn't be read. Please try again.", ErrorCode.VALIDATION_ERROR);
        }
    }

    private static ResponseEntity<byte[]> image(AttachmentContent content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.bytes());
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
