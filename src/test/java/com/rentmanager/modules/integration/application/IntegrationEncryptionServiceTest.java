package com.rentmanager.modules.integration.application;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-256-GCM credential encryption: round-trip, tamper detection, wrong-key
 * failure and key-material validation at construction. Test keys are the
 * base64 of all-zero/one-byte arrays — never real secrets.
 */
class IntegrationEncryptionServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(keyBytes(1));

    private static byte[] keyBytes(int lastByte) {
        byte[] bytes = new byte[32];
        bytes[31] = (byte) lastByte;
        return bytes;
    }

    @Test
    void roundTrip_returnsOriginalPlaintext() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");

        String encrypted = service.encrypt("{\"api_key\":\"secret\"}");

        assertThat(encrypted).isNotBlank();
        assertThat(service.decrypt(encrypted)).isEqualTo("{\"api_key\":\"secret\"}");
    }

    @Test
    void payloadHelpers_roundTrip() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");

        String payload = "{\"consumer_key\":\"ck\",\"consumer_secret\":\"cs\"}";

        assertThat(service.decryptPayload(service.encryptPayload(payload))).isEqualTo(payload);
    }

    @Test
    void encrypt_samePlaintextTwice_producesDistinctCiphertexts() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");

        assertThat(service.encrypt("same-value")).isNotEqualTo(service.encrypt("same-value"));
    }

    @Test
    void fallbackKey_isUsedWhenPrimaryKeyBlank() {
        IntegrationEncryptionService primary = new IntegrationEncryptionService("", OTHER_KEY);

        String encrypted = primary.encrypt("via-fallback");

        assertThat(primary.decrypt(encrypted)).isEqualTo("via-fallback");
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");

        String encrypted = service.encrypt("value");
        String tampered = "A" + encrypted.substring(1);

        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption key is unchanged");
    }

    @Test
    void decrypt_withWrongKey_throws() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");
        IntegrationEncryptionService otherService = new IntegrationEncryptionService(OTHER_KEY, "");

        String encrypted = service.encrypt("value");

        assertThatThrownBy(() -> otherService.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptNullAndDecryptNull_returnNull() {
        IntegrationEncryptionService service = new IntegrationEncryptionService(KEY, "");

        assertThat(service.encrypt(null)).isNull();
        assertThat(service.decrypt(null)).isNull();
    }

    @Test
    void constructor_withoutAnyKey_throws() {
        assertThatThrownBy(() -> new IntegrationEncryptionService("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No integration encryption key configured");
    }

    @Test
    void constructor_withNonBase64Key_throws() {
        assertThatThrownBy(() -> new IntegrationEncryptionService("not-base64!!", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid base64");
    }

    @Test
    void constructor_withWrongKeyLength_throws() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new IntegrationEncryptionService(shortKey, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 32 bytes");
    }
}
