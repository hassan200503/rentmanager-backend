package com.rentmanager.modules.unit.infrastructure.persistence.entity;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "units",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_units_tenant_unit_number",
                        columnNames = {"tenantId", "unitNumber"}
                )
        }
)
public class UnitJpaEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID propertyId;

    @Column(nullable = false)
    private String unitNumber;

    private String label;

    @Enumerated(EnumType.STRING)
    private UnitStatus status;

    @Enumerated(EnumType.STRING)
    private UnitOccupancyStatus occupancyStatus;

    private BigDecimal rentAmount;

    @Column(columnDefinition = "TEXT")
    private String description;
}