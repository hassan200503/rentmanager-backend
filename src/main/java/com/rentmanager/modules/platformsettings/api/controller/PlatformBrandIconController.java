package com.rentmanager.modules.platformsettings.api.controller;

import com.rentmanager.modules.platformsettings.application.service.PlatformBrandIconService;
import com.rentmanager.modules.platformsettings.infrastructure.persistence.PlatformBrandIconStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

/**
 * The platform icon, as an image.
 *
 * Unauthenticated on purpose: it is the favicon and the logo in the page
 * chrome, needed before anyone signs in. Nothing about the platform is
 * disclosed beyond the picture the owner chose.
 *
 * Cached for five minutes with an ETag from the upload time, so a new icon
 * appears promptly without re-sending a few kilobytes on every page load.
 */
@RestController
@RequestMapping("/api/v1/public/platform/branding")
@RequiredArgsConstructor
public class PlatformBrandIconController {

    private static final Duration CACHE_FOR = Duration.ofMinutes(5);

    private final PlatformBrandIconService brandIconService;

    @GetMapping("/logo")
    public ResponseEntity<byte[]> getLogo() {
        Optional<PlatformBrandIconStore.BrandIcon> icon = brandIconService.find();
        if (icon.isEmpty()) {
            // The clients all fall back to the built-in mark, so "no icon
            // configured" is an ordinary answer, not an error worth logging.
            return ResponseEntity.notFound().build();
        }

        PlatformBrandIconStore.BrandIcon found = icon.get();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(found.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_FOR).cachePublic())
                .eTag("\"" + found.updatedAt().toEpochMilli() + "\"")
                .body(found.bytes());
    }
}
