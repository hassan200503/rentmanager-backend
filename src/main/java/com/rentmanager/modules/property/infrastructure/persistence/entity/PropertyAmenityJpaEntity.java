package com.rentmanager.modules.property.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "property_amenities",
        indexes = {
                @Index(name = "idx_property_amenity_tenant", columnList = "tenant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_property_amenity_name_tenant",
                        columnNames = {"tenant_id", "name"}
                )
        }
)
@Getter
@Setter
public class PropertyAmenityJpaEntity extends BaseTenantEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private PropertyJpaEntity property;
}