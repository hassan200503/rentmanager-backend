package com.rentmanager.modules.property.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(
        name = "properties",
        indexes = {
                @Index(name = "idx_property_tenant", columnList = "tenant_id"),
                @Index(name = "idx_property_reference_code", columnList = "reference_code"),
                @Index(name = "idx_property_status", columnList = "status")
        }
)
@Getter
@Setter
public class PropertyJpaEntity extends BaseTenantEntity {

    @Column(name = "reference_code", nullable = false, unique = true)
    private String referenceCode;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "premises_type")
    private PremisesType premisesType;

    @Enumerated(EnumType.STRING)
    @Column(name = "occupancy_status", nullable = false)
    private OccupancyStatus occupancyStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private PropertyCategoryJpaEntity category;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Embedded
    private PropertyAddressJpaEntity address;

    // =====================================================
    // SAFE FACTORY (no null returns)
    // =====================================================
    public static PropertyJpaEntity create(UUID tenantId) {
        PropertyJpaEntity entity = new PropertyJpaEntity();
        entity.restoreTenantId(tenantId);

        // FIX: required fields must always be initialized
        entity.referenceCode = null; // MUST be set by service layer before save
        entity.status = PropertyStatus.DRAFT;
        entity.occupancyStatus = OccupancyStatus.VACANT;

        return entity;
    }

    // =====================================================
    // DOMAIN SAFE SETTERS
    // =====================================================
    public void setPropertyType(PropertyType propertyType) {
        this.propertyType = propertyType;
    }

    public void setOccupancyStatus(OccupancyStatus occupancyStatus) {
        this.occupancyStatus = occupancyStatus;
    }

    // =====================================================
    // SAFE GETTERS
    // =====================================================
    public PropertyType getPropertyType() {
        return this.propertyType;
    }

    public OccupancyStatus getOccupancyStatus() {
        return this.occupancyStatus;
    }
}