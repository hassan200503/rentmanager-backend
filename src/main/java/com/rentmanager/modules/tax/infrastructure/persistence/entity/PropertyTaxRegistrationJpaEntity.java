package com.rentmanager.modules.tax.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.tax.domain.enums.PropertyTaxRegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "property_tax_registrations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_property_tax_registration",
                        columnNames = {"tenant_id", "property_id"}
                )
        },
        indexes = {
                @Index(name = "idx_property_tax_reg_status", columnList = "tenant_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class PropertyTaxRegistrationJpaEntity extends BaseTenantEntity {

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "landlord_kra_pin")
    private String landlordKraPin;

    @Column(name = "tenant_kra_pin")
    private String tenantKraPin;

    @Column(name = "kr_property_registration_id")
    private String krPropertyRegistrationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PropertyTaxRegistrationStatus status;

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "last_error")
    private String lastError;
}