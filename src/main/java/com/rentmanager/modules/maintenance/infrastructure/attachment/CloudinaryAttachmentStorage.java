package com.rentmanager.modules.maintenance.infrastructure.attachment;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.rentmanager.modules.integration.bridge.MediaStorageClientResolver;
import com.rentmanager.modules.maintenance.application.attachment.AttachmentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Stores maintenance photos as Cloudinary "authenticated" assets: they cannot
 * be fetched without a signature generated with the account's API secret,
 * which only this server holds. The server fetches the bytes itself and
 * returns them to an authorised caller; no delivery URL leaves the backend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryAttachmentStorage implements AttachmentStorage {

    private final MediaStorageClientResolver resolver;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String put(String folder, byte[] bytes, String contentType) {
        Cloudinary cloudinary = resolver.resolve();
        try {
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "image",
                    "type", "authenticated",
                    "unique_filename", true,
                    "overwrite", false
            ));
            Object publicId = result.get("public_id");
            Object format = result.get("format");
            if (publicId == null) {
                throw new IllegalStateException("Storage did not return an id");
            }
            return format == null ? publicId.toString() : publicId + "." + format;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Photo upload failed. Please try again.", e);
        }
    }

    @Override
    public byte[] get(String storageKey) {
        Cloudinary cloudinary = resolver.resolve();
        String url = cloudinary.url()
                .resourceType("image")
                .type("authenticated")
                .signed(true)
                .secure(true)
                .generate(storageKey);
        byte[] body = restTemplate.getForObject(url, byte[].class);
        if (body == null) {
            throw new IllegalStateException("Photo not available");
        }
        return body;
    }

    @Override
    public void delete(String storageKey) {
        try {
            String publicId = storageKey.contains(".") ? storageKey.substring(0, storageKey.lastIndexOf('.')) : storageKey;
            resolver.resolve().uploader().destroy(publicId, ObjectUtils.asMap("type", "authenticated", "resource_type", "image"));
        } catch (Exception e) {
            log.warn("Photo delete failed: {}", e.getClass().getSimpleName());
        }
    }
}
