package com.rentmanager.modules.mobile.api;

import com.rentmanager.contract.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Public, unauthenticated configuration the mobile app reads before sign-in.
 *
 * {@code minimumSupportedVersion} is the escape hatch for a release that must
 * not stay in use (a security or money-correctness fix): the app refuses to
 * run below it and sends the person to the store. It should almost never
 * move; the API is kept backward compatible so older versions keep working.
 *
 * Values come from configuration so ops can raise the floor without a
 * backend release: MOBILE_MINIMUM_VERSION, MOBILE_LATEST_VERSION,
 * MOBILE_ANDROID_STORE_URL, MOBILE_IOS_STORE_URL.
 */
@RestController
@RequestMapping("/api/v1/public/mobile")
public class MobileAppConfigController {

    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    public record MobileAppConfigResponse(
            String minimumSupportedVersion,
            String latestVersion,
            String androidStoreUrl,
            String iosStoreUrl
    ) {}

    private final MobileAppConfigResponse config;

    public MobileAppConfigController(
            @Value("${mobile.minimum-version:1.0.0}") String minimumVersion,
            @Value("${mobile.latest-version:1.0.0}") String latestVersion,
            @Value("${mobile.android-store-url:}") String androidStoreUrl,
            @Value("${mobile.ios-store-url:}") String iosStoreUrl
    ) {
        if (!SEMVER.matcher(minimumVersion).matches() || !SEMVER.matcher(latestVersion).matches()) {
            // Fail at startup: a malformed floor would either lock everyone
            // out or be silently ignored by clients.
            throw new IllegalStateException("mobile.minimum-version and mobile.latest-version must be MAJOR.MINOR.PATCH");
        }
        this.config = new MobileAppConfigResponse(
                minimumVersion, latestVersion, blankToNull(androidStoreUrl), blankToNull(iosStoreUrl));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<MobileAppConfigResponse>> config() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.ok(config));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
