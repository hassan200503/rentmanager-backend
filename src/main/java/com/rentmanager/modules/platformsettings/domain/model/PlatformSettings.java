package com.rentmanager.modules.platformsettings.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform-wide owner configuration (the single-row {@code platform_settings}
 * aggregate). Every knob maps to a real behaviour:
 *
 * <ul>
 *   <li>{@code premiumGraceDays} - premium benefits continue this long after
 *       a failed/timed-out renewal before the scheduler auto-reverts the
 *       landlord to COMMISSION billing (anchored at the paid period end).</li>
 *   <li>{@code subscriptionPaymentExpiryMinutes} - stale PENDING subscription
 *       STK pushes are swept EXPIRED after this window.</li>
 *   <li>{@code disbursementMaxRetryAttempts} - per-disbursement B2C retry
 *       budget before the payout is flagged for manual attention.</li>
 *   <li>{@code revenue*} - canonical M-Pesa collection/payout identifiers for
 *       platform revenue (non-secret values only; secret Daraja credentials
 *       stay in environment configuration).</li>
 *   <li>{@code supportEmail}/{@code supportPhone} - platform support contact.</li>
 * </ul>
 */
public class PlatformSettings {

    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    public static final int DEFAULT_PREMIUM_GRACE_DAYS = 7;
    public static final int DEFAULT_SUBSCRIPTION_PAYMENT_EXPIRY_MINUTES = 30;
    public static final int DEFAULT_DISBURSEMENT_MAX_RETRY_ATTEMPTS = 3;

    private final int premiumGraceDays;
    private final int subscriptionPaymentExpiryMinutes;
    private final int disbursementMaxRetryAttempts;

    private final String revenueBusinessShortcode;
    private final String revenuePaybill;
    private final String revenueTill;
    private final String revenueB2CShortcode;
    private final String revenueMpesaPhone;

    private final String supportEmail;
    private final String supportPhone;

    private final String updatedBy;
    private final Instant updatedAt;
    private final Long version;

    private PlatformSettings(
            int premiumGraceDays,
            int subscriptionPaymentExpiryMinutes,
            int disbursementMaxRetryAttempts,
            String revenueBusinessShortcode,
            String revenuePaybill,
            String revenueTill,
            String revenueB2CShortcode,
            String revenueMpesaPhone,
            String supportEmail,
            String supportPhone,
            String updatedBy,
            Instant updatedAt,
            Long version
    ) {
        this.premiumGraceDays = premiumGraceDays;
        this.subscriptionPaymentExpiryMinutes = subscriptionPaymentExpiryMinutes;
        this.disbursementMaxRetryAttempts = disbursementMaxRetryAttempts;
        this.revenueBusinessShortcode = revenueBusinessShortcode;
        this.revenuePaybill = revenuePaybill;
        this.revenueTill = revenueTill;
        this.revenueB2CShortcode = revenueB2CShortcode;
        this.revenueMpesaPhone = revenueMpesaPhone;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static PlatformSettings defaults(String bootstrapActor) {
        return new PlatformSettings(
                DEFAULT_PREMIUM_GRACE_DAYS,
                DEFAULT_SUBSCRIPTION_PAYMENT_EXPIRY_MINUTES,
                DEFAULT_DISBURSEMENT_MAX_RETRY_ATTEMPTS,
                null, null, null, null, null,
                null, null,
                bootstrapActor,
                Instant.now(),
                0L
        );
    }

    public static PlatformSettings rehydrate(
            int premiumGraceDays,
            int subscriptionPaymentExpiryMinutes,
            int disbursementMaxRetryAttempts,
            String revenueBusinessShortcode,
            String revenuePaybill,
            String revenueTill,
            String revenueB2CShortcode,
            String revenueMpesaPhone,
            String supportEmail,
            String supportPhone,
            String updatedBy,
            Instant updatedAt,
            Long version
    ) {
        return new PlatformSettings(
                premiumGraceDays,
                subscriptionPaymentExpiryMinutes,
                disbursementMaxRetryAttempts,
                revenueBusinessShortcode,
                revenuePaybill,
                revenueTill,
                revenueB2CShortcode,
                revenueMpesaPhone,
                supportEmail,
                supportPhone,
                updatedBy,
                updatedAt,
                version
        );
    }

    /**
     * Returns a new immutable instance with the owner-supplied values.
     * Never mutates the aggregate in place - the persistence layer stores
     * the returned instance.
     */
    public PlatformSettings reconfigure(
            int premiumGraceDays,
            int subscriptionPaymentExpiryMinutes,
            int disbursementMaxRetryAttempts,
            String revenueBusinessShortcode,
            String revenuePaybill,
            String revenueTill,
            String revenueB2CShortcode,
            String revenueMpesaPhone,
            String supportEmail,
            String supportPhone,
            String updatedBy
    ) {
        return new PlatformSettings(
                premiumGraceDays,
                subscriptionPaymentExpiryMinutes,
                disbursementMaxRetryAttempts,
                normalize(revenueBusinessShortcode),
                normalize(revenuePaybill),
                normalize(revenueTill),
                normalize(revenueB2CShortcode),
                normalize(revenueMpesaPhone),
                normalize(supportEmail),
                normalize(supportPhone),
                updatedBy,
                Instant.now(),
                version == null ? 0L : version
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public int getPremiumGraceDays() {
        return premiumGraceDays;
    }

    public int getSubscriptionPaymentExpiryMinutes() {
        return subscriptionPaymentExpiryMinutes;
    }

    public int getDisbursementMaxRetryAttempts() {
        return disbursementMaxRetryAttempts;
    }

    public String getRevenueBusinessShortcode() {
        return revenueBusinessShortcode;
    }

    public String getRevenuePaybill() {
        return revenuePaybill;
    }

    public String getRevenueTill() {
        return revenueTill;
    }

    public String getRevenueB2CShortcode() {
        return revenueB2CShortcode;
    }

    public String getRevenueMpesaPhone() {
        return revenueMpesaPhone;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getSupportPhone() {
        return supportPhone;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
