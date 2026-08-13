package com.rentmanager.modules.integration.bridge;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rentmanager.modules.integration.application.IntegrationNotConfiguredException;
import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Resolves the platform Cloudinary client through the Integration Registry —
 * the single source of truth for media storage. Preference order:
 *
 * <ol>
 *   <li>the ACTIVE {@code media_storage} config from the Integrations Console
 *       (encrypted, database-backed, switchable Development ⇄ Production
 *       without a redeploy);</li>
 *   <li>the legacy {@code cloudinary.*} environment bean (migration fallback —
 *       keeps existing deployments runnable until the Owner saves values);</li>
 *   <li>{@link IntegrationNotConfiguredException} — callers degrade the
 *       upload feature with a clear, Owner-facing message.</li>
 * </ol>
 *
 * <p>The resolved client is cached and rebuilt automatically when the active
 * credential set changes, so the next upload after a console save or
 * environment switch already uses the new client — no restart required.</p>
 */
@Slf4j
@Component
public class MediaStorageClientResolver {

    private final IntegrationRegistry registry;
    private final ObjectProvider<Cloudinary> envFallbackClient;

    private volatile String cachedFingerprint;
    private volatile Cloudinary cachedClient;

    public MediaStorageClientResolver(
            IntegrationRegistry registry,
            ObjectProvider<Cloudinary> envFallbackClient
    ) {
        this.registry = registry;
        this.envFallbackClient = envFallbackClient;
    }

    /**
     * The Cloudinary client for the currently-active media storage config.
     *
     * @throws IntegrationNotConfiguredException when neither the console
     *         config nor the environment fallback is fully configured
     */
    public Cloudinary resolve() {
        var resolved = registry.resolveOrNull(ProviderCatalog.MEDIA_STORAGE);
        if (resolved != null) {
            Map<String, String> c = resolved.credentials();
            String cloudName = c.getOrDefault("cloud_name", "");
            String apiKey = c.getOrDefault("api_key", "");
            String apiSecret = c.getOrDefault("api_secret", "");
            if (!cloudName.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank()) {
                String fingerprint = cloudName + "|" + apiKey;
                if (cachedClient == null || !fingerprint.equals(cachedFingerprint)) {
                    cachedClient = build(cloudName, apiKey, apiSecret);
                    cachedFingerprint = fingerprint;
                    log.info("Media storage client rebuilt from registry config (cloud={}, db={})",
                            cloudName, resolved.fromDatabase());
                }
                return cachedClient;
            }
        }

        Cloudinary envClient = envFallbackClient.getIfAvailable();
        if (envClient != null) {
            return envClient;
        }
        throw new IntegrationNotConfiguredException(ProviderCatalog.MEDIA_STORAGE);
    }

    private Cloudinary build(String cloudName, String apiKey, String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
}