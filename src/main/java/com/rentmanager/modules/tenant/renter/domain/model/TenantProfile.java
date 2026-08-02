package com.rentmanager.modules.tenant.renter.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.tenant.renter.domain.event.TenantProfileCreatedEvent;

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

    /**
     * Explicit consent for WhatsApp broadcasts from the landlord. Meta
     * Business Policy requires opt-in before any business-initiated
     * template message; consent is captured via a real checkbox in the
     * renter portal and is NEVER assumed - defaults to false. Renters
     * without opt-in still receive SMS/email/in-app deliveries; WhatsApp
     * is simply skipped for them.
     */
    private boolean whatsappOptIn;

    protected TenantProfile() {
    }

    /**
     * Creates a TenantProfile from a confirmed reservation, after the Clerk
     * account has been created.
     *
     * @param landlordTenantId the SaaS account (landlord) this profile belongs under
     * @param correlationId    ties this creation event to the originating saga
     *                         (e.g. the reservation id) — added as part of the
     *                         event-publish sweep; see ReservationFulfillmentOrchestrator,
     *                         which now passes reservation.getId().toString(), matching
     *                         the correlation id already used for the sibling Unit/Lease
     *                         events registered in that same saga step.
     */
    public static TenantProfile create(
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId,
            String correlationId
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

        profile.registerEvent(new TenantProfileCreatedEvent(
                landlordTenantId,
                profile.getId(),
                correlationId,
                profile.getId()
        ));

        return profile;
    }

    /**
     * Convenience overload for call sites that have no consent data
     * (dev setup, tests): opt-in defaults to FALSE - consent is never
     * assumed.
     */
    public static TenantProfile rehydrate(
            UUID id,
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId
    ) {
        return rehydrate(id, landlordTenantId, clerkUserId, fullName, email, phone, nationalId, false);
    }

    public static TenantProfile rehydrate(
            UUID id,
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId,
            boolean whatsappOptIn
    ) {
        TenantProfile profile = new TenantProfile();
        profile.setId(id);
        profile.assignTenant(landlordTenantId);
        profile.clerkUserId = clerkUserId;
        profile.fullName = fullName;
        profile.email = email;
        profile.phone = phone;
        profile.nationalId = nationalId;
        profile.whatsappOptIn = whatsappOptIn;
        return profile;
    }

    public void updateDetails(String fullName, String email, String phone, String nationalId) {
        if (fullName != null && !fullName.isBlank()) {
            this.fullName = fullName;
        }
        if (email != null && !email.isBlank()) {
            this.email = email;
        }
        if (phone != null && !phone.isBlank()) {
            this.phone = phone;
        }
        if (nationalId != null && !nationalId.isBlank()) {
            this.nationalId = nationalId;
        }
    }

    /**
     * Explicit renter consent for WhatsApp broadcasts. The renter must
     * actively opt in (checkbox in the portal); opting out clears it.
     * Broadcast WhatsApp delivery is only attempted for profiles where
     * this is true.
     */
    public void updateWhatsAppOptIn(boolean enabled) {
        this.whatsappOptIn = enabled;
    }

    public String getClerkUserId() { return clerkUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
    public boolean isWhatsAppOptIn() { return whatsappOptIn; }
}