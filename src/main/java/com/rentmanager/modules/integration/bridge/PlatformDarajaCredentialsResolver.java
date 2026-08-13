package com.rentmanager.modules.integration.bridge;

import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.io.ByteArrayInputStream;

/**
 * Resolves the platform-wide Daraja (M-Pesa) credentials through the
 * Integration Registry — the single source of truth for STK collection,
 * B2C disbursement and Ratiba flows. The registry falls back to the legacy
 * {@code daraja.*} environment properties while no console config exists,
 * so every deployment keeps working before the Owner saves values.
 *
 * <p>B2C {@code SecurityCredential}: when a {@code security_certificate}
 * is configured, the raw {@code initiator_password} is RSA-encrypted with
 * Safaricom's X.509 public certificate for that environment at request
 * time (certificates differ between sandbox and production). Without a
 * certificate the stored value is sent as-is — the legacy path where the
 * Owner stores the precomputed SecurityCredential directly.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformDarajaCredentialsResolver {

    private final IntegrationRegistry registry;

    public record PlatformDarajaCredentials(
            String consumerKey,
            String consumerSecret,
            String businessShortCode,
            String passkey,
            String baseUrl,
            String initiatorName,
            String securityCredential,
            String resultUrl,
            String queueTimeOutUrl
    ) {}

    /**
     * The currently-active platform Daraja credentials. Values are the
     * database config when configured, otherwise the legacy environment
     * fallback (which may be placeholder sentinels — those reach Safaricom
     * and fail loudly, exactly as before this control plane existed).
     */
    public PlatformDarajaCredentials credentials() {
        Map<String, String> c = resolvedCredentials();
        String initiatorPassword = c.getOrDefault("initiator_password", "");
        String securityCredential = deriveSecurityCredential(
                initiatorPassword,
                c.getOrDefault("security_certificate", ""));
        return new PlatformDarajaCredentials(
                c.getOrDefault("consumer_key", ""),
                c.getOrDefault("consumer_secret", ""),
                c.getOrDefault("business_shortcode", ""),
                c.getOrDefault("passkey", ""),
                c.getOrDefault("base_url", ""),
                c.getOrDefault("initiator_name", ""),
                securityCredential,
                c.getOrDefault("result_url", ""),
                c.getOrDefault("queue_timeout_url", ""));
    }

    /**
     * Convenience for flows that only need the STK credential set.
     */
    public com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials stkCredentials() {
        PlatformDarajaCredentials c = credentials();
        return com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials.of(
                c.consumerKey(), c.consumerSecret(), c.businessShortCode(), c.passkey());
    }

    private Map<String, String> resolvedCredentials() {
        var resolved = registry.resolveOrNull(ProviderCatalog.DARAJA);
        return resolved == null ? Map.of() : resolved.credentials();
    }

    private String deriveSecurityCredential(String initiatorPassword, String certificate) {
        if (initiatorPassword == null || initiatorPassword.isBlank()) {
            return "";
        }
        if (certificate == null || certificate.isBlank()) {
            // Legacy path: the stored value IS the precomputed SecurityCredential.
            return initiatorPassword;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate x509 = (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(decodeCertificate(certificate)));
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, x509.getPublicKey());
            byte[] encrypted = cipher.doFinal(initiatorPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            // Never fall back to sending the raw password — that would be a
            // silent security downgrade. Surface the real reason instead.
            throw new IllegalStateException(
                    "Failed to derive the Daraja B2C SecurityCredential from the configured "
                            + "Safaricom certificate — check security_certificate is a valid base64 "
                            + "X.509 public certificate for this environment", e);
        }
    }

    private static byte[] decodeCertificate(String certificate) {
        String value = certificate.trim();
        if (value.contains("-----BEGIN")) {
            value = value
                    .replaceAll("-----BEGIN [A-Z ]*-----", "")
                    .replaceAll("-----END [A-Z ]*-----", "")
                    .replaceAll("\\s", "");
        }
        return Base64.getDecoder().decode(value);
    }
}