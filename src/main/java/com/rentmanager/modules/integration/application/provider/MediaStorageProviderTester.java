package com.rentmanager.modules.integration.application.provider;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Real Cloudinary test: uploads a tiny marker file, reads it back and
 * deletes it — so a write-only IAM/missing-delete misconfiguration is
 * caught before it becomes a production incident.
 */
@Slf4j
@Component
public class MediaStorageProviderTester implements ProviderTester {

    /**
     * A real, minimal 1x1 PNG so the marker exercises the same image upload
     * path (resource_type=image) the platform uses for property/unit photos —
     * not just the generic storage path.
     */
    private static final byte[] MARKER = createMarkerPng();

    private static byte[] createMarkerPng() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Cloudinary test marker PNG", e);
        }
    }

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
                    MARKER,
                    ObjectUtils.asMap("public_id", publicId, "overwrite", true, "resource_type", "image")
            );

            // Verify the marker is actually readable back — otherwise a
            // write/read misconfiguration could pass the test silently.
            String uploadedUrl = nonBlank(upload, "secure_url");
            if (uploadedUrl == null) {
                return TestResult.failure("Upload did not return a usable URL",
                        "Cloudinary accepted the marker but the response contained no secure_url/url — "
                                + "check the account's upload permission.");
            }

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

    private static String nonBlank(Map<?, ?> values, String key) {
        String v = String.valueOf(values.get(key));
        return v == null || v.isBlank() || "null".equalsIgnoreCase(v) ? null : v;
    }
}