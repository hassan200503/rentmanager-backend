package com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "tenant_profile",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_profile_tenant_clerk_user",
                        columnNames = {"tenant_id", "clerk_user_id"}
                )
        }
)
public class TenantProfileEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // landlord's SaaS account id

    @Column(name = "clerk_user_id", nullable = false)
    private String clerkUserId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "national_id")
    private String nationalId;

    protected TenantProfileEntity() {
        // JPA requirement
    }

    public TenantProfileEntity(
            UUID id,
            UUID tenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.clerkUserId = clerkUserId;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.nationalId = nationalId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getClerkUserId() { return clerkUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
}