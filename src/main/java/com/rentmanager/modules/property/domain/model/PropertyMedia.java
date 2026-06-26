package com.rentmanager.modules.property.domain.model;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.MediaType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PropertyMedia extends BaseTenantEntity {

    private UUID propertyId;

    private MediaType mediaType;

    private String fileUrl;

    private String fileName;

    /**
     * Cloudinary public_id used for delete/replace operations.
     */
    private String publicId;

    private String contentType;

    private Long fileSize;

    private boolean primaryMedia;

    private Instant uploadedAt;

    private String caption;

    private int sortOrder;

    // =====================================================
    // FACTORY
    // =====================================================

    public static PropertyMedia create(
            UUID tenantId,
            UUID propertyId,
            MediaType mediaType,
            String fileUrl,
            String fileName,
            String publicId,
            String contentType,
            Long fileSize,
            boolean primaryMedia,
            Instant uploadedAt,
            String caption,
            int sortOrder
    ) {
        PropertyMedia media = PropertyMedia.builder()
                .propertyId(propertyId)
                .mediaType(mediaType)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .publicId(publicId)
                .contentType(contentType)
                .fileSize(fileSize)
                .primaryMedia(primaryMedia)
                .uploadedAt(uploadedAt)
                .caption(caption)
                .sortOrder(sortOrder)
                .build();

        media.setId(UUID.randomUUID());   // ← add this line
        media.assignTenant(tenantId);

        return media;
    }

    // =====================================================
    // REHYDRATE
    // =====================================================

    public static PropertyMedia rehydrate(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            MediaType mediaType,
            String fileUrl,
            String fileName,
            String publicId,
            String contentType,
            Long fileSize,
            boolean primaryMedia,
            Instant uploadedAt,
            String caption,
            int sortOrder,
            Long version
    ) {
        PropertyMedia media = PropertyMedia.builder()
                .propertyId(propertyId)
                .mediaType(mediaType)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .publicId(publicId)
                .contentType(contentType)
                .fileSize(fileSize)
                .primaryMedia(primaryMedia)
                .uploadedAt(uploadedAt)
                .caption(caption)
                .sortOrder(sortOrder)
                .build();

        media.restoreId(id);
        media.restoreTenantId(tenantId);
        media.setVersion(version);

        return media;
    }
}