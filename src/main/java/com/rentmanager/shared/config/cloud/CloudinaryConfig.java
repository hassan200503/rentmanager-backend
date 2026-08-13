package com.rentmanager.shared.config.cloud;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    /**
     * Environment-level Cloudinary client, used as the migration fallback by
     * {@link com.rentmanager.modules.integration.bridge.MediaStorageClientResolver}.
     *
     * <p>Returns {@code null} when any {@code cloudinary.*} value is unset
     * instead of failing startup: once the Integrations Console is live the
     * database config is the source of truth, and an incomplete env set must
     * degrade the upload feature gracefully rather than brick the whole app.
     * The upload service surfaces a clear "configure Media Storage" message
     * in that case.</p>
     */
    @Bean
    public Cloudinary cloudinary() {
        if (cloudName == null || cloudName.isBlank()
                || apiKey == null || apiKey.isBlank()
                || apiSecret == null || apiSecret.isBlank()) {
            return null;
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }
}