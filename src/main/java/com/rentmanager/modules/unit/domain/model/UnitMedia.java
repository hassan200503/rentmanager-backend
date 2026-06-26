package com.rentmanager.modules.unit.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnitMedia {

    private UUID id;
    private UUID tenantId;
    private UUID unitId;

    private String url;
    private String type; // IMAGE, VIDEO, DOCUMENT
    private String caption;

    private boolean primary;
    private int sortOrder;

    // =====================================================
    // FACTORY
    // =====================================================
    public static UnitMedia create(
            UUID tenantId,
            UUID unitId,
            String url,
            String type,
            String caption,
            boolean primary,
            int sortOrder
    ) {
        return UnitMedia.builder()
                .id(UUID.randomUUID())   // ← add this line
                .tenantId(tenantId)
                .unitId(unitId)
                .url(url)
                .type(type)
                .caption(caption)
                .primary(primary)
                .sortOrder(sortOrder)
                .build();
    }

    // =====================================================
    // REHYDRATE
    // =====================================================
    public static UnitMedia rehydrate(
            UUID id,
            UUID tenantId,
            UUID unitId,
            String url,
            String type,
            String caption,
            boolean primary,
            int sortOrder
    ) {
        return UnitMedia.builder()
                .id(id)
                .tenantId(tenantId)
                .unitId(unitId)
                .url(url)
                .type(type)
                .caption(caption)
                .primary(primary)
                .sortOrder(sortOrder)
                .build();
    }
}