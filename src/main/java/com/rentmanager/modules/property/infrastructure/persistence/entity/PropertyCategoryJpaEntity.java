package com.rentmanager.modules.property.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "property_categories",
        indexes = {
                @Index(name = "idx_property_category_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_property_category_name_tenant",
                        columnNames = {"tenant_id", "name"}
                )
        }
)
@Getter
@Setter
public class PropertyCategoryJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;
}