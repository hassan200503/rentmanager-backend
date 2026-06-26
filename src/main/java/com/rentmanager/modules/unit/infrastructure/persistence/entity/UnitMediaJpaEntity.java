package com.rentmanager.modules.unit.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "unit_media",
        indexes = {
                @Index(name = "idx_unit_media_tenant", columnList = "tenant_id"),
                @Index(name = "idx_unit_media_unit",   columnList = "unit_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitMediaJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false, updatable = false)
    private UUID unitId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "type", nullable = false)
    private String type;                   // IMAGE, VIDEO, DOCUMENT

    @Column(name = "caption")
    private String caption;

    @Column(name = "primary_media", nullable = false)
    private boolean primaryMedia = false;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Version
    private Long version;
}