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
     * Renter's KRA PIN (currently optional/conditional). Captured only when
     * the renter supplies it — needed at eTIMS invoice level only when the
     * renter claims the rental expense / input VAT, and required for
     * certain eRITS flows to be confirmed by a tax advisor (brief item A1).
     * Never a hard requirement for the rental-receipt path.
     */
    private String kraPin;

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
        return rehydrate(id, landlordTenantId, clerkUserId, fullName, email, phone, nationalId, false, null);
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
        return rehydrate(id, landlordTenantId, clerkUserId, fullName, email, phone, nationalId, whatsappOptIn, null);
    }

    public static TenantProfile rehydrate(
            UUID id,
            UUID landlordTenantId,
            String clerkUserId,
            String fullName,
            String email,
            String phone,
            String nationalId,
            boolean whatsappOptIn,
            String kraPin
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
        profile.kraPin = kraPin;
        return profile;
    }

    /**
     * Sets or clears the renter's KRA PIN. Blank input clears the pin.
     */
    public void updateKraPin(String kraPin) {
        this.kraPin = kraPin != null && kraPin.isBlank() ? null : kraPin;
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
    /**
     * Detaches this renter record from a login that has been deleted. The
     * profile itself is the landlord's record of their tenant and is kept;
     * it simply no longer belongs to any sign-in. Marketing consent is
     * withdrawn with the account.
     */
    /**
     * A renter entered by their landlord, who has no Clerk account yet.
     *
     * The reservation saga's {@link #create} requires a Clerk user id and an
     * email because the account exists before the profile there. A landlord
     * recording an existing tenancy has neither: the tenant may only have a
     * phone number, and may never sign in at all (a caretaker collects the
     * cash). Phone is therefore the required identifier; email is optional and
     * is what later links this record to a Clerk identity.
     *
     * @param phone E.164 already normalised by the caller (KenyanMsisdn)
     */
    public static TenantProfile createForLandlord(
            UUID landlordTenantId,
            String fullName,
            String phone,
            String email,
            String nationalId,
            String correlationId
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("landlordTenantId is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("fullName is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }

        TenantProfile profile = new TenantProfile();
        profile.setId(UUID.randomUUID());
        profile.assignTenant(landlordTenantId);
        profile.clerkUserId = null;
        profile.fullName = fullName.trim();
        profile.phone = phone.trim();
        profile.email = (email == null || email.isBlank()) ? null : email.trim();
        profile.nationalId = (nationalId == null || nationalId.isBlank()) ? null : nationalId.trim();

        profile.registerEvent(new TenantProfileCreatedEvent(
                landlordTenantId,
                profile.getId(),
                correlationId,
                profile.getId()
        ));

        return profile;
    }

    /** True while no Clerk identity has claimed this tenancy record. */
    public boolean isUnlinked() {
        return clerkUserId == null || clerkUserId.isBlank();
    }

    /**
     * Claims this landlord-entered tenancy for a signed-in Clerk identity.
     *
     * Refuses when a different identity already holds it: re-pointing a
     * profile would hand one person another's rent history and portal.
     */
    public void linkIdentity(String newClerkUserId) {
        if (newClerkUserId == null || newClerkUserId.isBlank()) {
            throw new IllegalArgumentException("clerkUserId is required");
        }
        if (!isUnlinked() && !newClerkUserId.equals(clerkUserId)) {
            throw new IllegalStateException(
                    "Tenant profile " + getId() + " is already linked to another identity");
        }
        this.clerkUserId = newClerkUserId;
    }

    public void unlinkIdentity(String tombstone) {
        if (tombstone == null || tombstone.isBlank()) {
            throw new IllegalArgumentException("Tombstone is required");
        }
        this.clerkUserId = tombstone;
        this.whatsappOptIn = false;
    }

    public void updateWhatsAppOptIn(boolean enabled) {
        this.whatsappOptIn = enabled;
    }

    public String getClerkUserId() { return clerkUserId; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getNationalId() { return nationalId; }
    public String getKraPin() { return kraPin; }
    public boolean isWhatsAppOptIn() { return whatsappOptIn; }
}