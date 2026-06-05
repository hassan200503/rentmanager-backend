package com.rentmanager.modules.property.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private PropertyCategoryJpaEntity category;


    @Column(name = "owner_id")
    private UUID ownerId;

    @Embedded
    private PropertyAddressJpaEntity address;

    public static PropertyJpaEntity create(UUID tenantId) {
        return null;
    }

    public void setTenantId(UUID tenantId) {

    }

    public void setPropertyType(PropertyType propertyType) {
    }

    public void setOccupancyStatus(OccupancyStatus occupancyStatus) {
    }

    public PropertyType getPropertyType() {
        PropertyType PropertyType = getPropertyType();
        return PropertyType;
    }

    public OccupancyStatus getOccupancyStatus() {
        OccupancyStatus OccupancyStatus = getOccupancyStatus();
        return OccupancyStatus;
    }
}
