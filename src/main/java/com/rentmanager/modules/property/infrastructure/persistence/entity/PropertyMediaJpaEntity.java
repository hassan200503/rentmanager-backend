package com.rentmanager.modules.property.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "property_media",
        indexes = {
                @Index(name = "idx_property_media_tenant", columnList = "tenant_id"),
                @Index(name = "idx_property_media_property", columnList = "property_id")
        }
)
@Getter
@Setter
public class PropertyMediaJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "primary_media", nullable = false)
    private Boolean primaryMedia = false;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Version
    private Long version;
}