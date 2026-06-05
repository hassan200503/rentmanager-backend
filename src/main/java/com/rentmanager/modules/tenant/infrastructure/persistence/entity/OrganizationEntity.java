package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "organizations",
        indexes = {
                @Index(name = "idx_org_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_org_code", columnList = "organization_code")
        }
)
@NoArgsConstructor
public class OrganizationEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "organization_code", nullable = false, unique = true, length = 50)
    private String organizationCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by")
    private UUID createdBy;
}