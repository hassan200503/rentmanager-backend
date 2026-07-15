package com.rentmanager.modules.property.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.event.PropertyActivatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyArchivedEvent;
import com.rentmanager.modules.property.domain.event.PropertyCreatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyOccupancyChangedEvent;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;


@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Property extends AggregateRoot {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "reference_code", nullable = false, unique = true)
    private String referenceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OccupancyStatus occupancyStatus;

    @Embedded
    private Address address;

    @Embedded
    private GeoLocation geoLocation;

    @Embedded
    private PropertyDimensions dimensions;

    @Column(length = 2000)
    private String description;

    // -----------------------------
    // FACTORY METHOD (CREATION)
    // -----------------------------
    public static Property create(
            UUID tenantId,
            String name,
            PropertyType propertyType,
            Address address,
            GeoLocation geoLocation,
            PropertyDimensions dimensions,
            String description,
            String correlationId
    ) {

        // =====================================================
        // STRICT DOMAIN INVARIANTS (THIS WAS MISSING)
        // =====================================================



        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        if (propertyType == null) {
            throw new IllegalArgumentException("propertyType is required");
        }

        UUID propertyId = UUID.randomUUID();

        Property property = Property.builder()
                .tenantId(tenantId)
                .name(name)
                .referenceCode("PROP-" + propertyId.toString())
                .propertyType(propertyType)
                .status(PropertyStatus.DRAFT) // safer than INACTIVE for lifecycle tests
                .occupancyStatus(OccupancyStatus.VACANT)
                .address(address)
                .geoLocation(geoLocation)
                .dimensions(dimensions)
                .description(description)
                .build();

        property.setId(propertyId);

        property.registerEvent(new PropertyCreatedEvent(
                tenantId,
                correlationId,
                propertyId,
                propertyId
        ));

        return property;
    }
    // -----------------------------
    // LIFECYCLE METHODS
    // -----------------------------
    public void activate(String correlationId) {
        if (this.status == PropertyStatus.ACTIVE) return;

        this.status = PropertyStatus.ACTIVE;

        registerEvent(new PropertyActivatedEvent(
                tenantId,
                getId(),
                correlationId,
                getId()
        ));
    }

    public void archive(String correlationId) {
        if (this.status == PropertyStatus.ARCHIVED) return;

        this.status = PropertyStatus.ARCHIVED;

        registerEvent(new PropertyArchivedEvent(
                tenantId,
                getId(),
                correlationId,
                getId()
        ));
    }

    // -----------------------------
    // OCCUPANCY MANAGEMENT
    // -----------------------------
    public void markFullyOccupied(String correlationId) {
        OccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = OccupancyStatus.FULLY_OCCUPIED;

        registerEvent(new PropertyOccupancyChangedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                previous.ordinal(),
                this.occupancyStatus.ordinal()
        ));
    }

    public void markVacant(String correlationId) {
        OccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = OccupancyStatus.VACANT;

        registerEvent(new PropertyOccupancyChangedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                previous.ordinal(),
                this.occupancyStatus.ordinal()
        ));
    }

    public boolean isArchived() {
        return this.status == PropertyStatus.ARCHIVED;
    }

    public void updateDetails(String name, String description) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }

        if (description != null) {
            this.description = description;
        }
    }

    public void archive() {
        archive("SYSTEM");
    }

    protected void assignTenantInternal(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public boolean belongsToTenant(UUID tenantId) {
        return this.tenantId != null && this.tenantId.equals(tenantId);
    }


    public static Property rehydrate(
            UUID id,
            UUID tenantId,
            String name,
            String referenceCode,
            PropertyType propertyType,
            PropertyStatus status,
            OccupancyStatus occupancyStatus,
            Address address,
            GeoLocation geoLocation,
            PropertyDimensions dimensions,
            String description
    ) {
        Property property = Property.builder()
                .tenantId(tenantId)
                .name(name)
                .referenceCode(referenceCode)
                .propertyType(propertyType)
                .status(status)
                .occupancyStatus(occupancyStatus)
                .address(address)
                .geoLocation(geoLocation)
                .dimensions(dimensions)
                .description(description)
                .build();

        property.setId(id); // allowed ONLY here internally if protected in BaseEntity

        return property;
    }









    public void markPartiallyOccupied(String correlationId) {
        OccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = OccupancyStatus.PARTIALLY_OCCUPIED;

        registerEvent(new PropertyOccupancyChangedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                previous.ordinal(),
                this.occupancyStatus.ordinal()
        ));
    }








}
