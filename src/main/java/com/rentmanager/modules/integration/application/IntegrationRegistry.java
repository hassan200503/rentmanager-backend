package com.rentmanager.modules.integration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import com.rentmanager.modules.integration.domain.repository.IntegrationConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Integration Registry — the single place runtime code resolves provider
 * credentials. Business logic never reads provider secrets from
 * environment variables or configuration properties directly; it asks the
 * registry for the ACTIVE configuration of a provider.
 *
 * Resolution order:
 *   1. the active {@link IntegrationConfig} row (database, encrypted)
 *   2. the pre-existing environment variables / Spring properties for that
 *      provider (migration fallback — keeps every existing deployment
 *      runnable until the owner saves values through the console)
 *
 * Resolution is cached for 60 seconds and invalidated on every save /
 * activate / test so environment switches take effect without a redeploy.
 * Sentinel placeholder values in the environment (e.g. "YOUR_CONSUMER_KEY")
 * are treated as unconfigured.
 */
@Slf4j
@Service
public class IntegrationRegistry {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String JSON_ENCRYPTION_KEY_VERSION = "key_version";

    private final IntegrationConfigRepository repository;
    private final IntegrationEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private final ConcurrentHashMap<String, ResolvedConfig> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> cacheExpiry = new ConcurrentHashMap<>();

    public IntegrationRegistry(
            IntegrationConfigRepository repository,
            IntegrationEncryptionService encryptionService,
            ObjectMapper objectMapper,
            Environment environment
    ) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    public record ResolvedConfig(
            String providerKey,
            IntegrationEnvironment environment,
            Map<String, String> credentials,
            boolean fromDatabase
    ) {}

    /**
     * The active credentials for a provider. Throws
     * {@link IntegrationNotConfiguredException} when the provider has no
     * active config and no environment fallback — callers must catch it.
     */
    public ResolvedConfig resolve(String providerKey) throws IntegrationNotConfiguredException {
        ResolvedConfig resolved = resolveOrNull(providerKey);
        if (resolved == null) {
            throw new IntegrationNotConfiguredException(providerKey);
        }
        return resolved;
    }

    public ResolvedConfig resolveOrNull(String providerKey) {
        if (!ProviderCatalog.exists(providerKey)) {
            return null;
        }
        return cached(providerKey);
    }

    /**
     * The saved (possibly inactive) credentials for one environment — used
     * by Test Connection and the console preview. Falls back to environment
     * properties when no saved row exists.
     */
    public Map<String, String> resolveSaved(String providerKey, IntegrationEnvironment environment) {
        Optional<IntegrationConfig> saved = repository.find(providerKey, environment);
        if (saved.isPresent() && saved.get().hasCredentials()) {
            try {
                return decryptPayload(saved.get().getEncryptedCredentials());
            } catch (Exception e) {
                log.warn("Failed to decrypt saved {} config for {} — falling back to environment",
                        environment, providerKey, e);
            }
        }
        return environmentFallback(providerKey);
    }

    public IntegrationEnvironment activeEnvironment(String providerKey) {
        ResolvedConfig resolved = resolveOrNull(providerKey);
        return resolved != null ? resolved.environment() : null;
    }

    public boolean isConfigured(String providerKey) {
        return resolveOrNull(providerKey) != null;
    }

    public void invalidate(String providerKey) {
        cache.remove(providerKey);
        cacheExpiry.remove(providerKey);
    }

    // ---------------------------------------------------------------
    // Resolution
    // ---------------------------------------------------------------

    private ResolvedConfig cached(String providerKey) {
        Instant expiry = cacheExpiry.get(providerKey);
        if (expiry != null && expiry.isAfter(Instant.now())) {
            ResolvedConfig cached = cache.get(providerKey);
            if (cached != null) {
                return cached;
            }
        }

        ResolvedConfig resolved = resolveFresh(providerKey);
        if (resolved != null) {
            cache.put(providerKey, resolved);
            cacheExpiry.put(providerKey, Instant.now().plus(CACHE_TTL));
        }
        return resolved;
    }

    private ResolvedConfig resolveFresh(String providerKey) {
        Optional<IntegrationConfig> active = repository.findActive(providerKey);
        if (active.isPresent() && active.get().hasCredentials()) {
            try {
                Map<String, String> credentials = decryptPayload(active.get().getEncryptedCredentials());
                if (hasAnyValue(credentials)) {
                    return new ResolvedConfig(providerKey, active.get().getEnvironment(), credentials, true);
                }
            } catch (Exception e) {
                log.warn("Failed to decrypt active {} integration config — falling back to environment",
                        providerKey, e);
            }
        }

        Map<String, String> fallback = environmentFallback(providerKey);
        if (!hasAnyValue(fallback)) {
            return null;
        }
        return new ResolvedConfig(providerKey, inferFallbackEnvironment(providerKey, fallback), fallback, false);
    }

    private Map<String, String> decryptPayload(String encrypted) {
        try {
            String json = encryptionService.decryptPayload(encrypted);
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse decrypted integration payload", e);
        }
    }

    private static boolean hasAnyValue(Map<String, String> credentials) {
        return credentials.values().stream().anyMatch(v -> v != null && !v.isBlank());
    }

    // ---------------------------------------------------------------
    // Environment fallbacks (migration bridge)
    // ---------------------------------------------------------------

    /**
     * Reads this provider's legacy configuration keys from the Spring
     * environment (application.yml + env vars). Sentinel placeholder values
     * such as "YOUR_CONSUMER_KEY" are dropped so an unset provider resolves
     * to null rather than a fake credential set.
     */
    private Map<String, String> environmentFallback(String providerKey) {
        return switch (providerKey) {
            case ProviderCatalog.DARAJA -> Map.of(
                    "consumer_key", prop("daraja.consumer-key"),
                    "consumer_secret", prop("daraja.consumer-secret"),
                    "business_shortcode", prop("daraja.business-short-code"),
                    "passkey", prop("daraja.passkey"),
                    "initiator_name", prop("daraja.b2c.initiator-name"),
                    "initiator_password", prop("daraja.b2c.security-credential"),
                    "base_url", prop("daraja.base-url"),
                    "result_url", prop("daraja.b2c.result-url"),
                    "queue_timeout_url", prop("daraja.b2c.queue-time-out-url"));
            case ProviderCatalog.AFRICASTALKING -> Map.of(
                    "username", prop("africastalking.username"),
                    "api_key", prop("africastalking.api-key"),
                    "sender_id", prop("africastalking.sender-id"),
                    "base_url", prop("africastalking.base-url"));
            case ProviderCatalog.WHATSAPP -> Map.of();
            case ProviderCatalog.EMAIL -> Map.of(
                    "host", prop("spring.mail.host"),
                    "port", prop("spring.mail.port"),
                    "username", prop("spring.mail.username"),
                    "password", prop("spring.mail.password"),
                    "from_address", prop("spring.mail.properties.mail.from"),
                    "from_name", "",
                    "tls", prop("spring.mail.properties.mail.smtp.starttls.enable"));
            case ProviderCatalog.MEDIA_STORAGE -> Map.of(
                    "cloud_name", prop("cloudinary.cloud-name"),
                    "api_key", prop("cloudinary.api-key"),
                    "api_secret", prop("cloudinary.api-secret"));
            case ProviderCatalog.CLERK -> Map.of(
                    "publishable_key", prop("clerk.publishable-key"),
                    "secret_key", prop("clerk.secret-key"),
                    "webhook_signing_secret", prop("clerk.webhook-signing-secret"),
                    "base_url", prop("clerk.base-url"));
            default -> Map.of();
        };
    }

    private String prop(String key) {
        String value = environment.getProperty(key, "");
        if (value == null || value.isBlank() || isSentinel(value)) {
            return "";
        }
        return value;
    }

    private static boolean isSentinel(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String v = value.trim().toLowerCase();
        return v.equals("yours") || v.equals("change-me") || v.equals("changeme")
                || v.equals("your_consumer_key") || v.equals("your_consumer_secret")
                || v.equals("your_daraja_passkey") || v.equals("your_at_api_key")
                || v.equals("your_clerk_secret_key")
                || v.startsWith("your_");
    }

    private IntegrationEnvironment inferFallbackEnvironment(String providerKey, Map<String, String> fallback) {
        if (providerKey.equals(ProviderCatalog.DARAJA)) {
            String baseUrl = fallback.getOrDefault("base_url", "");
            return baseUrl.toLowerCase().contains("sandbox") ? IntegrationEnvironment.DEVELOPMENT : IntegrationEnvironment.PRODUCTION;
        }
        if (providerKey.equals(ProviderCatalog.AFRICASTALKING)) {
            String baseUrl = fallback.getOrDefault("base_url", "");
            return baseUrl.toLowerCase().contains("sandbox") ? IntegrationEnvironment.DEVELOPMENT : IntegrationEnvironment.PRODUCTION;
        }
        return IntegrationEnvironment.PRODUCTION;
    }
}