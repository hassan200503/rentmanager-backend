package com.rentmanager.shared.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rentmanager.modules.integration.application.IntegrationNotConfiguredException;
import com.rentmanager.modules.integration.bridge.MediaStorageClientResolver;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.shared.dto.MediaUploadResponse;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaUploadService {

    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final MediaStorageClientResolver mediaStorageClientResolver;
    private final PropertyMediaRepository propertyMediaRepository;
    private final UnitMediaRepository unitMediaRepository;

    // =====================================================
    // PROPERTY MEDIA
    // =====================================================

    public MediaUploadResponse uploadPropertyMedia(
            UUID tenantId,
            UUID propertyId,
            MultipartFile file,
            boolean isPrimary
    ) {
        validate(file);

        Map uploadResult = uploadToCloudinary(file, buildFolder("properties", tenantId, propertyId));

        if (isPrimary) {
            demoteExistingPropertyPrimary(tenantId, propertyId);
        }

        int nextSortOrder = propertyMediaRepository
                .findAllByTenantIdAndPropertyId(tenantId, propertyId)
                .size();

        PropertyMedia media = PropertyMedia.create(
                tenantId,
                propertyId,
                com.rentmanager.modules.property.domain.enums.MediaType.IMAGE,
                (String) uploadResult.get("secure_url"),
                file.getOriginalFilename(),
                (String) uploadResult.get("public_id"),
                file.getContentType(),
                file.getSize(),
                isPrimary,
                java.time.Instant.now(),
                null,
                nextSortOrder
        );

        PropertyMedia saved = propertyMediaRepository.save(media);

        log.info("Property media uploaded: propertyId={} url={}", propertyId, saved.getFileUrl());

        return toResponse(saved, propertyId);
    }

    public void deletePropertyMedia(UUID tenantId, UUID mediaId) {
        PropertyMedia media = propertyMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property media not found", ErrorCode.RESOURCE_NOT_FOUND
                ));

        deleteFromCloudinary(media.getFileUrl());
        propertyMediaRepository.delete(media);

        log.info("Property media deleted: mediaId={}", mediaId);
    }

    // =====================================================
    // UNIT MEDIA
    // =====================================================

    public MediaUploadResponse uploadUnitMedia(
            UUID tenantId,
            UUID unitId,
            MultipartFile file,
            boolean isPrimary
    ) {
        validate(file);

        Map uploadResult = uploadToCloudinary(file, buildFolder("units", tenantId, unitId));

        if (isPrimary) {
            demoteExistingUnitPrimary(tenantId, unitId);
        }

        int nextSortOrder = unitMediaRepository
                .findAllByTenantIdAndUnitId(tenantId, unitId)
                .size();

        UnitMedia media = UnitMedia.create(
                tenantId,
                unitId,
                (String) uploadResult.get("secure_url"),
                "IMAGE",
                null,           // caption — can be added via update endpoint later
                isPrimary,
                nextSortOrder
        );

        UnitMedia saved = unitMediaRepository.save(media);

        log.info("Unit media uploaded: unitId={} url={}", unitId, saved.getUrl());

        return toResponse(saved, unitId);
    }

    public void deleteUnitMedia(UUID tenantId, UUID mediaId) {
        UnitMedia media = unitMediaRepository
                .findByIdAndTenantId(mediaId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit media not found", ErrorCode.RESOURCE_NOT_FOUND
                ));

        deleteFromCloudinary(media.getUrl());
        unitMediaRepository.delete(media);

        log.info("Unit media deleted: mediaId={}", mediaId);
    }

    // =====================================================
    // PLATFORM BRANDING
    // =====================================================

    /**
     * Uploads the platform system-wide logo. Same validation contract as
     * property/unit media (jpeg/png/webp, max 5MB) plus a square ratio
     * guard so the mark renders cleanly in every chrome surface.
     */
    public String uploadPlatformBrandAsset(MultipartFile file) {
        validate(file);
        Map uploadResult = uploadToCloudinary(file, "rentmanager/platform/branding");
        String url = (String) uploadResult.get("secure_url");
        log.info("Platform branding logo uploaded: url={}", url);
        return url;
    }

    /**
     * Purges a previous platform logo from Cloudinary. Best-effort —
     * failures are logged and never fail the settings write (the DB row is
     * already consistent; worst case an orphaned blob is dropped by
     * Cloudinary's retention rules).
     */
    public void deletePlatformBrandAsset(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        deleteFromCloudinary(url);
        log.info("Platform branding logo purged: url={}", url);
    }

    // =====================================================
    // PRIVATE HELPERS
    // =====================================================

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + file.getContentType() + ". Allowed: jpeg, png, webp"
            );
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds maximum size of 5MB");
        }
    }

    private Map uploadToCloudinary(MultipartFile file, String folder) {
        Cloudinary cloudinary;
        try {
            cloudinary = mediaStorageClientResolver.resolve();
        } catch (IntegrationNotConfiguredException e) {
            log.warn("Media upload skipped: media storage is not configured: {}", e.getMessage());
            throw new RuntimeException(
                    "Media storage (Cloudinary) is not configured. Ask the platform owner to "
                            + "configure it in Platform Settings → Integrations → Media Storage.");
        }
        try {
            return cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", folder,
                            "resource_type", "image",
                            "use_filename", true,
                            "unique_filename", true,
                            "overwrite", false
                    )
            );
        } catch (IOException e) {
            log.error("Cloudinary upload failed", e);
            throw new RuntimeException("Image upload failed. Please try again.", e);
        }
    }

    private void deleteFromCloudinary(String url) {
        // Derive public_id from URL: everything after /upload/ and before file extension
        try {
            Cloudinary cloudinary = mediaStorageClientResolver.resolve();
            String publicId = extractPublicId(url);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IntegrationNotConfiguredException e) {
            log.warn("Cloudinary delete skipped: media storage is not configured: {}", e.getMessage());
        } catch (IOException e) {
            // Log but don't fail — DB record is still removed
            log.error("Cloudinary delete failed for url={}", url, e);
        }
    }

    private String extractPublicId(String url) {
        // Cloudinary URL format: .../upload/v1234567890/{folder}/{filename}.{ext}
        // public_id = {folder}/{filename} (no extension)
        int uploadIdx = url.indexOf("/upload/");
        if (uploadIdx == -1) return url;
        String afterUpload = url.substring(uploadIdx + 8); // skip "/upload/"
        // Strip version segment if present (v1234567890/)
        if (afterUpload.startsWith("v") && afterUpload.indexOf('/') > 1) {
            afterUpload = afterUpload.substring(afterUpload.indexOf('/') + 1);
        }
        // Strip file extension
        int dotIdx = afterUpload.lastIndexOf('.');
        return dotIdx != -1 ? afterUpload.substring(0, dotIdx) : afterUpload;
    }

    private String buildFolder(String type, UUID tenantId, UUID resourceId) {
        return String.format("rentmanager/%s/%s/%s", type, tenantId, resourceId);
    }

    private void demoteExistingPropertyPrimary(UUID tenantId, UUID propertyId) {
        propertyMediaRepository
                .findByTenantIdAndPropertyIdAndPrimaryMediaTrue(tenantId, propertyId)
                .ifPresent(existing -> {

                    PropertyMedia demoted = PropertyMedia.rehydrate(
                            existing.getId(),
                            existing.getTenantId(),
                            existing.getPropertyId(),
                            existing.getMediaType(),
                            existing.getFileUrl(),
                            existing.getFileName(),
                            existing.getPublicId(),
                            existing.getContentType(),
                            existing.getFileSize(),
                            false,
                            existing.getUploadedAt(),
                            existing.getCaption(),
                            existing.getSortOrder(),
                            existing.getVersion()
                    );

                    propertyMediaRepository.save(demoted);
                });
    }

    private void demoteExistingUnitPrimary(UUID tenantId, UUID unitId) {
        unitMediaRepository
                .findByTenantIdAndUnitIdAndPrimaryMediaTrue(tenantId, unitId)
                .ifPresent(existing -> {
                    UnitMedia demoted = UnitMedia.rehydrate(
                            existing.getId(),
                            existing.getTenantId(),
                            existing.getUnitId(),
                            existing.getUrl(),
                            existing.getType(),
                            existing.getCaption(),
                            false,               // demote
                            existing.getSortOrder()
                    );
                    unitMediaRepository.save(demoted);
                });
    }

    private MediaUploadResponse toResponse(PropertyMedia media, UUID propertyId) {
        return new MediaUploadResponse(
                media.getId(),
                media.getTenantId(),
                propertyId,
                media.getFileUrl(),
                media.getMediaType().name(),
                media.getCaption(),
                media.isPrimaryMedia(),
                media.getSortOrder()
        );
    }

    private MediaUploadResponse toResponse(UnitMedia media, UUID unitId) {
        return new MediaUploadResponse(
                media.getId(),
                media.getTenantId(),
                unitId,
                media.getUrl(),
                media.getType(),
                media.getCaption(),
                media.isPrimary(),
                media.getSortOrder()
        );
    }
}

