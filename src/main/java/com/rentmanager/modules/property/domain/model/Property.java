package com.rentmanager.modules.property.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.event.PropertyActivatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyArchivedEvent;
import com.rentmanager.modules.property.domain.event.PropertyCreatedEvent;
import com.rentmanager.modules.property.domain.event.PropertyOccupancyChangedEvent;
import com.rentmanager.modules.property.domain.event.PropertyUpdatedEvent;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
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
    @Column(name = "premises_type")
    private PremisesType premisesType;

    /**
     * Mandatory free-text justification when the premises classification is
     * explicitly set (an override of the auto-derivation, including
     * MIXED_USE). NULL for auto-classified rows. This is a legal/tax
     * attribute driving the MRI/VAT pipeline, so ungoverned silent overrides
     * are forbidden.
     */
    @Column(length = 500)
    private String premisesTypeOverrideReason;

    /**
     * Authenticated user id that set the premises classification.
     * Together with {@link #premisesTypeChangedAt} this forms the audit
     * trail for the classification, mirroring the persisted columns.
     */
    private UUID premisesTypeChangedBy;

    private Instant premisesTypeChangedAt;

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
    // CLASSIFICATION
    // -----------------------------

    /**
     * Returns the persisted premises classification, deriving it from the
     * property type only when no explicit classification is stored. Newly
     * created properties always carry the derived value; rehydrated rows may
     * (before V57 backfill) be null up to the getter.
     */
    public PremisesType getPremisesType() {
        return premisesType != null
                ? premisesType
                : PremisesType.fromPropertyType(propertyType);
    }

    // -----------------------------
    // FACTORY METHOD (CREATION)
    // -----------------------------
    /**
     * Auto-classified creation: premises type is derived from {@code
     * propertyType} (never MIXED_USE); no audit fields are recorded.
     */
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
        return create(
                tenantId,
                name,
                propertyType,
                null,
                null,
                null,
                address,
                geoLocation,
                dimensions,
                description,
                correlationId
        );
    }

    /**
     * Creation with an explicitly supplied premises classification.
     *
     * <p>When {@code premisesType} is non-null (an override of the
     * auto-derivation — including the only route to MIXED_USE) a non-blank
     * {@code premisesTypeOverrideReason} and a non-null {@code changedBy} are
     * mandatory, and {@code premisesTypeChangedAt} is stamped here. When it is
     * null the classification is derived from {@code propertyType}; the audit
     * fields are not recorded, and {@code changedBy} (the caller's user id) is
     * simply not persisted.
     */
    public static Property create(
            UUID tenantId,
            String name,
            PropertyType propertyType,
            PremisesType premisesType,
            String premisesTypeOverrideReason,
            UUID changedBy,
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

        boolean isOverride = premisesType != null;

        if (isOverride) {
            if (premisesTypeOverrideReason == null || premisesTypeOverrideReason.isBlank()) {
                throw new IllegalArgumentException(
                        "premisesTypeOverrideReason is required when premisesType is provided"
                );
            }
            if (premisesTypeOverrideReason.length() > 500) {
                throw new IllegalArgumentException(
                        "premisesTypeOverrideReason must not exceed 500 characters"
                );
            }
            if (changedBy == null) {
                throw new IllegalArgumentException(
                        "changedBy is required when premisesType is provided"
                );
            }
        } else {
            if (premisesTypeOverrideReason != null && !premisesTypeOverrideReason.isBlank()) {
                throw new IllegalArgumentException(
                        "premisesTypeOverrideReason cannot be provided without an explicit premisesType"
                );
            }
            premisesType = PremisesType.fromPropertyType(propertyType);
        }

        UUID propertyId = UUID.randomUUID();

        Property property = Property.builder()
                .tenantId(tenantId)
                .name(name)
                .referenceCode("PROP-" + propertyId.toString())
                .propertyType(propertyType)
                .premisesType(premisesType)
                .premisesTypeOverrideReason(isOverride ? premisesTypeOverrideReason : null)
                .premisesTypeChangedBy(isOverride ? changedBy : null)
                .premisesTypeChangedAt(isOverride ? Instant.now() : null)
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

        registerEvent(new PropertyUpdatedEvent(
                tenantId,
                "PROP-UPDATE-" + getId(),
                getId(),
                getId()
        ));
    }

    /**
     * Changes the physical property type, keeping the premises classification
     * consistent with the classification invariant:
     *
     * <ul>
     *   <li>no audited override exists (override reason null) — the premises
     *       classification is re-derived from the new type (never MIXED_USE),
     *       and any stale audit fields are cleared;</li>
     *   <li>an audited override exists — the classification is sticky and
     *       survives the type change (the override reason/audit trail stays,
     *       since classification is a deliberate legal/tax decision).</li>
     * </ul>
     */
    public void changeType(PropertyType newType, String correlationId) {
        if (newType == null) {
            throw new IllegalArgumentException("propertyType is required");
        }
        if (this.propertyType == newType) {
            return;
        }

        this.propertyType = newType;

        if (this.premisesTypeOverrideReason == null) {
            this.premisesType = PremisesType.fromPropertyType(newType);
            this.premisesTypeChangedBy = null;
            this.premisesTypeChangedAt = null;
        }

        registerEvent(new PropertyUpdatedEvent(
                tenantId,
                correlationId != null ? correlationId : "PROP-UPDATE-" + getId(),
                getId(),
                getId()
        ));
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
        return rehydrate(
                id, tenantId, name, referenceCode, propertyType,
                null, status, occupancyStatus, address, geoLocation,
                dimensions, description
        );
    }

    public static Property rehydrate(
            UUID id,
            UUID tenantId,
            String name,
            String referenceCode,
            PropertyType propertyType,
            PremisesType premisesType,
            PropertyStatus status,
            OccupancyStatus occupancyStatus,
            Address address,
            GeoLocation geoLocation,
            PropertyDimensions dimensions,
            String description
    ) {
        return rehydrate(
                id, tenantId, name, referenceCode, propertyType,
                premisesType, null, null, null,
                status, occupancyStatus, address, geoLocation,
                dimensions, description
        );
    }

    public static Property rehydrate(
            UUID id,
            UUID tenantId,
            String name,
            String referenceCode,
            PropertyType propertyType,
            PremisesType premisesType,
            String premisesTypeOverrideReason,
            UUID premisesTypeChangedBy,
            Instant premisesTypeChangedAt,
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
                .premisesType(premisesType)
                .premisesTypeOverrideReason(premisesTypeOverrideReason)
                .premisesTypeChangedBy(premisesTypeChangedBy)
                .premisesTypeChangedAt(premisesTypeChangedAt)
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
