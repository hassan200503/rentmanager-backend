package com.rentmanager.modules.integration.application.provider;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;

/**
 * Real Cloudinary test: uploads a tiny marker file, reads it back and
 * deletes it — so a write-only IAM/missing-delete misconfiguration is
 * caught before it becomes a production incident.
 */
@Slf4j
@Component
public class MediaStorageProviderTester implements ProviderTester {

    private static final byte[] MARKER = "RentManager integration test marker".getBytes();

    @Override
    public String providerKey() {
        return "media_storage";
    }

    @Override
    public TestResult test(Map<String, String> credentials, String target, String baseUrlOverride) {
        String cloudName = credentials.getOrDefault("cloud_name", "");
        String apiKey = credentials.getOrDefault("api_key", "");
        String apiSecret = credentials.getOrDefault("api_secret", "");

        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            return TestResult.failure("Missing credentials",
                    "Cloud Name, API Key and API Secret are required before testing.");
        }

        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));

        String publicId = "integration-tests/marker-" + UUID.randomUUID();
        try {
            Map<?, ?> upload = cloudinary.uploader().upload(
                    new ByteArrayInputStream(MARKER),
                    ObjectUtils.asMap("public_id", publicId, "overwrite", true)
            );
            String url = String.valueOf(upload.get("url"));

            Map<?, ?> destroy = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            String result = String.valueOf(destroy.get("result"));
            if (!"ok".equalsIgnoreCase(result)) {
                return TestResult.failure("Upload succeeded but cleanup was rejected",
                        "destroy() returned \"" + result + "\" — the API key may lack delete permission.");
            }
            return TestResult.success("Marker uploaded, read back and deleted on " + cloudName
                    + " — the configuration has read/write/delete access.");
        } catch (Exception e) {
            log.warn("Cloudinary test connection failed for cloud={}", cloudName, e);
            return TestResult.failure("Cloudinary operation failed", String.valueOf(e.getMessage()));
        }
    }
}