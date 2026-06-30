package com.rentmanager.modules.tenant.renter.domain.model;

import com.rentmanager.domain.base.AggregateRoot;

import java.util.UUID;

/**
 * Represents a renter's profile under a landlord's account — distinct from
 * the SaaS-level "tenant" (the landlord/account owner) used elsewhere in this
 * codebase as BaseTenantEntity.tenantId. Naming collision is unfortunate but
 * pre-existing; "tenantId" here (inherited via assignTenant) refers to the
 * landlord's account, while this class itself represents the renter.
 */
public class TenantProfile extends AggregateRoot {

    private String clerkUserId;
    private String fullName;
    private String email;
    private String phone;
    private String nationalId;

    protected TenantProfile() {
    }

    /**
     * Creates a TenantProfile from a confirmed reservation, after the Clerk
     * account has been created.
     *
     * @param landlordTenantId the SaaS account (landlord) this profile belongs under
     */
    public static TenantProfile create(
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("landlordTenantId is required");
        }
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("clerkUserId is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }

        TenantProfile profile = new TenantProfile();
        profile.setId(UUID.randomUUID());
        profile.assignTenant(landlordTenantId);
        profile.clerkUserId = clerkUserId;
        profile.fullName = fullName;
        profile.email = email;
        profile.phone = phone;
        profile.nationalId = nationalId;

        return profile;
    }

    public static TenantProfile rehydrate(
            UUID id,
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId
    ) {
        TenantProfile profile = new TenantProfile();
        profile.setId(id);
        profile.assignTenant(landlordTenantId);
        profile.clerkUserId = clerkUserId;
        profile.fullName = fullName;
        profile.email = email;
        profile.phone = phone;
        profile.nationalId = nationalId;
        return profile;
    }

    public String getClerkUserId() { return clerkUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
}