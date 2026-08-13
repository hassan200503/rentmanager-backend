package com.rentmanager.modules.integration.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for integration credentials at rest.
 *
 * Format on disk: base64(IV(12) || ciphertext || GCM tag(16)) — single
 * VARCHAR column. Each record tracks its {@code key_version}; rotating the
 * master key means re-encrypting every row with the new key and a bumped
 * version (see the rotation section in GO_LIVE_RUNBOOK.md).
 *
 * The key comes from INTEGRATION_ENCRYPTION_KEY, falling back to the
 * pre-existing DARAJA_CREDENTIALS_ENCRYPTION_KEY so existing deployments
 * migrate with zero configuration. Never commit a real key.
 */
@Service
public class IntegrationEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec secretKey;

    public IntegrationEncryptionService(
            @Value("${integration.encryption-key:}") String configuredKey,
            @Value("${daraja.credentials-encryption-key:}") String fallbackKey
    ) {
        String key = configuredKey == null || configuredKey.isBlank() ? fallbackKey : configuredKey;
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "No integration encryption key configured. Set INTEGRATION_ENCRYPTION_KEY "
                            + "(or the existing DARAJA_CREDENTIALS_ENCRYPTION_KEY) to a base64-encoded "
                            + "32-byte AES-256 key before storing integration credentials.");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Integration encryption key is not valid base64.", e);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "Integration encryption key must decode to exactly 32 bytes (AES-256), got "
                            + keyBytes.length + " bytes.");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
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
            // Never leak plaintext or key material into the exception.
            throw new IllegalStateException("Failed to encrypt integration credential field", e);
        }
    }

    public String decrypt(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(storedValue);
            if (combined.length < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8) {
                throw new IllegalStateException("Ciphertext too short");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to decrypt integration credentials — check the encryption key is unchanged "
                            + "since this value was written",
                    e);
        }
    }

    /**
     * Encrypts a full credential payload (JSON) at the given key version.
     */
    public String encryptPayload(String plaintextPayload) {
        return encrypt(plaintextPayload);
    }

    /**
     * Decrypts a full credential payload (JSON).
     */
    public String decryptPayload(String storedPayload) {
        return decrypt(storedPayload);
    }
}