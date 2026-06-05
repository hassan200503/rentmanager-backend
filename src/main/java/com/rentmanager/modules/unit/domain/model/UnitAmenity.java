package com.rentmanager.modules.unit.domain.model;

import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UnitAmenity {

    private UUID id;
    private UUID tenantId;
    private UUID unitId;

    private String name;
    private String description;

    private boolean active;

    // =====================================================
    // FACTORY METHOD
    // =====================================================
    public static UnitAmenity create(
            UUID tenantId,
            UUID unitId,
            String name,
            String description
    ) {
        return UnitAmenity.builder()
                .tenantId(tenantId)
                .unitId(unitId)
                .name(name)
                .description(description)
                .active(true)
                .build();
    }

    // =====================================================
    // REHYDRATION (DB → DOMAIN)
    // =====================================================
    public static UnitAmenity rehydrate(
            UUID id,
            UUID tenantId,
            UUID unitId,
            String name,
            String description,
            boolean active
    ) {
        return UnitAmenity.builder()
                .id(id)
                .tenantId(tenantId)
                .unitId(unitId)
                .name(name)
                .description(description)
                .active(active)
                .build();
    }

    // =====================================================
    // BUSINESS BEHAVIOR
    // =====================================================
    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}