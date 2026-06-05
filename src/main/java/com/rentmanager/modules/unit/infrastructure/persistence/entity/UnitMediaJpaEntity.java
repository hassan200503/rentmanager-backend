package com.rentmanager.modules.unit.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "unit_media")
public class UnitMediaJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID unitId;

    @Column(nullable = false)
    private String url;

    private String type;

    @Column(columnDefinition = "TEXT")
    private String caption;

    @Column(nullable = false)
    private boolean primaryMedia;

    private int sortOrder;
}