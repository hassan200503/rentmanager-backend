package com.rentmanager.modules.tenant.infrastructure.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts Daraja consumer secrets / passkeys at rest using AES-256-GCM.
 * Applied via @Convert on individual fields of DarajaCredentials — never
 * store these values in plaintext, since they are real M-Pesa financial
 * credentials capable of authorizing STK pushes against a landlord's Till.
 *
 * Key is sourced from DARAJA_CREDENTIALS_ENCRYPTION_KEY (base64-encoded
 * 32-byte AES-256 key), generated once via e.g.:
 *   openssl rand -base64 32
 * and stored as a real secret in your deployment environment — NEVER
 * committed to application.yml or version control.
 *
 * Format on disk: base64(IV || ciphertext || GCM tag), single string, so a
 * plain VARCHAR column is sufficient — no schema changes needed if the
 * plaintext column already exists (though a wider column is safer, since
 * ciphertext is longer than plaintext).
 *
 * IMPORTANT: rotating this key requires re-encrypting every existing row,
 * or all previously-stored credentials become unreadable. There is no
 * key-versioning built into this converter; if key rotation is a near-term
 * requirement, that should be added before this goes into production use
 * with real landlord credentials, not after.
 *
 * @Component is required (not just @Converter) so Spring can inject the
 * key via @Value; JPA converters are normally instantiated directly by
 * Hibernate rather than through the Spring context, so `autoApply = false`
 * plus explicit @Convert(converter = ...) on each field, combined with
 * Spring Data JPA's auto-registration of @Component-annotated converters,
 * is what makes DI actually work here.
 */
@Component
@Converter
public class DarajaCredentialEncryptionConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    public DarajaCredentialEncryptionConverter(
            @Value("${daraja.credentials-encryption-key}") String base64Key
    ) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "daraja.credentials-encryption-key is not configured. Set " +
                    "DARAJA_CREDENTIALS_ENCRYPTION_KEY to a base64-encoded 32-byte key " +
                    "before storing or reading per-landlord Daraja credentials."
            );
        }
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "daraja.credentials-encryption-key must decode to exactly 32 bytes " +
                    "(AES-256), got " + keyBytes.length + " bytes."
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Deliberately does not leak plaintext or key material into the
            // exception message or logs — only the failure itself.
            throw new IllegalStateException("Failed to encrypt Daraja credential field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue);

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt Daraja credential field — " +
                    "check DARAJA_CREDENTIALS_ENCRYPTION_KEY is unchanged since this value was written", e);
        }
    }
}