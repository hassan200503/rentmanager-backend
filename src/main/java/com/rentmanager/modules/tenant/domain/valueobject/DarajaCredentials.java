package com.rentmanager.modules.tenant.domain.valueobject;

import com.rentmanager.modules.tenant.infrastructure.persistence.converter.DarajaCredentialEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Per-landlord M-Pesa Daraja credentials, embedded on Tenant. Every field
 * is encrypted at rest via DarajaCredentialEncryptionConverter — these are
 * real financial credentials capable of authorizing STK pushes against a
 * specific landlord's Till/Paybill, not just low-sensitivity config.
 *
 * Deliberately does NOT include callbackUrl, callbackSecret, or baseUrl —
 * those remain global, sourced from DarajaProperties, since they describe
 * routing back to this platform's own single callback endpoint rather than
 * anything landlord-specific.
 *
 * `configured` is a plain boolean flag (not derived from null-checking the
 * other fields) so callers can cheaply check "has this landlord set up
 * M-Pesa yet" with a single field read, without decrypting anything.
 */
@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DarajaCredentials {

    @Convert(converter = DarajaCredentialEncryptionConverter.class)
    @Column(name = "daraja_consumer_key", length = 500)
    private String consumerKey;

    @Convert(converter = DarajaCredentialEncryptionConverter.class)
    @Column(name = "daraja_consumer_secret", length = 500)
    private String consumerSecret;

    @Convert(converter = DarajaCredentialEncryptionConverter.class)
    @Column(name = "daraja_business_short_code", length = 500)
    private String businessShortCode;

    @Convert(converter = DarajaCredentialEncryptionConverter.class)
    @Column(name = "daraja_passkey", length = 500)
    private String passkey;

    @Column(name = "daraja_configured", nullable = false)
    private boolean configured;

    private DarajaCredentials(
            String consumerKey,
            String consumerSecret,
            String businessShortCode,
            String passkey
    ) {
        if (consumerKey == null || consumerKey.isBlank()) {
            throw new IllegalArgumentException("Daraja consumer key is required");
        }
        if (consumerSecret == null || consumerSecret.isBlank()) {
            throw new IllegalArgumentException("Daraja consumer secret is required");
        }
        if (businessShortCode == null || businessShortCode.isBlank()) {
            throw new IllegalArgumentException("Daraja business short code is required");
        }
        if (passkey == null || passkey.isBlank()) {
            throw new IllegalArgumentException("Daraja passkey is required");
        }

        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.businessShortCode = businessShortCode;
        this.passkey = passkey;
        this.configured = true;
    }

    public static DarajaCredentials of(
            String consumerKey,
            String consumerSecret,
            String businessShortCode,
            String passkey
    ) {
        return new DarajaCredentials(consumerKey, consumerSecret, businessShortCode, passkey);
    }

    /**
     * Represents "not yet configured" — the default state for a newly
     * created Tenant, before the landlord has entered their Till details.
     * Distinct from null on the Tenant field itself so Tenant.builder()-style
     * construction and JPA embeddable semantics stay simple (an @Embeddable
     * field is typically never null on the owning entity; its own fields
     * being null/false is what signals "unconfigured").
     */
    public static DarajaCredentials unconfigured() {
        return new DarajaCredentials();
    }
}