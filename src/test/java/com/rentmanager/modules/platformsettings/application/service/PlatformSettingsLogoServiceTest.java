package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformSettingsResponse;
import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.shared.service.MediaUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System-wide logo lifecycle.
 *
 * The icon now lives in our own database (V104) rather than Cloudinary: with no
 * Cloudinary credentials — the free-tier default — the old upload failed
 * outright, so the owner could not change the icon at all. The guarantees this
 * test held onto are unchanged: one upload replaces the icon everywhere, a
 * replaced Cloudinary asset is still purged, removal is idempotent, and every
 * change is audited. Manual mocks, no Mockito extension, per repo conventions.
 */
class PlatformSettingsLogoServiceTest {

    private static final String OLD_URL =
            "https://res.cloudinary.com/rentmanager/image/upload/rentmanager/platform/branding/old_x7p2k9.png";
    private static final String STORED_ICON_PATH = "/api/v1/public/platform/branding/logo";

    private PlatformSettingsRepository repository;
    private AuditService auditService;
    private MediaUploadService mediaUploadService;
    private PlatformBrandIconService brandIconService;
    private IntegrationRegistry integrationRegistry;
    private PlatformSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformSettingsRepository.class);
        auditService = mock(AuditService.class);
        mediaUploadService = mock(MediaUploadService.class);
        brandIconService = mock(PlatformBrandIconService.class);
        integrationRegistry = mock(IntegrationRegistry.class);
        service = new PlatformSettingsService(
                repository, auditService, mediaUploadService, brandIconService,
                "https://sandbox.safaricom.co.ke", "RentManager", integrationRegistry);
    }

    private static MockMultipartFile png(byte[] bytes) {
        return new MockMultipartFile("file", "logo.png", "image/png", bytes);
    }

    private static PlatformSettings settingsWithLogo(String logoUrl) {
        return PlatformSettings.rehydrate(
                7, 30, 3, 30,
                null, null, null, null, null,
                null, null,
                logoUrl,
                "owner-1",
                java.time.Instant.now(),
                0L
        );
    }

    @Test
    void uploadLogo_storesTheIconInOurOwnDatabase() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));

        service.uploadLogo(png(new byte[]{1, 2, 3}), "owner-1");

        verify(brandIconService).upload(any(), eq("owner-1"));
        // Nothing is uploaded to Cloudinary any more: that dependency is what
        // made changing the icon impossible without a media provider.
        verify(mediaUploadService, never()).uploadPlatformBrandAsset(any());
    }

    @Test
    void uploadLogo_purgesAndClearsAReplacedCloudinaryAsset() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadLogo(png(new byte[]{1, 2, 3}), "owner-1");

        verify(mediaUploadService).deletePlatformBrandAsset(OLD_URL);
        ArgumentCaptor<PlatformSettings> saved = ArgumentCaptor.forClass(PlatformSettings.class);
        verify(repository).save(saved.capture());
        // One answer to "which icon is current?": the stored bytes.
        assertThat(saved.getValue().getLogoUrl()).isNull();
    }

    @Test
    void uploadLogo_whenNoPreviousCloudinaryAsset_doesNotAttemptPurgeOrWriteTheRow() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));

        service.uploadLogo(png(new byte[]{1, 2, 3}), "owner-1");

        verify(mediaUploadService, never()).deletePlatformBrandAsset(any());
        verify(repository, never()).save(any());
    }

    @Test
    void uploadLogo_recordsBrandingAuditEvent() throws Exception {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));

        service.uploadLogo(png(new byte[]{1, 2, 3}), "owner-1");

        ArgumentCaptor<AuditLog> audit = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditService).record(audit.capture());
        assertThat(field(audit.getValue(), "action")).isEqualTo("PLATFORM_BRANDING_UPDATE");
        assertThat(field(audit.getValue(), "actorId")).isEqualTo("owner-1");
        assertThat(field(audit.getValue(), "metadata")).isEqualTo("{\"action\":\"logo_uploaded\"}");
    }

    private static Object field(AuditLog log, String name) throws Exception {
        java.lang.reflect.Field f = AuditLog.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(log);
    }

    @Test
    void removeLogo_clearsTheStoredIconAndPurgesAnyCloudinaryAsset() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSettingsResponse response = service.removeLogo("owner-1");

        assertThat(response.platform().logoUrl()).isNull();
        verify(brandIconService).remove("owner-1");
        verify(mediaUploadService).deletePlatformBrandAsset(OLD_URL);
        ArgumentCaptor<PlatformSettings> saved = ArgumentCaptor.forClass(PlatformSettings.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLogoUrl()).isNull();
    }

    @Test
    void removeLogo_whenNoCloudinaryAssetConfigured_isIdempotentAndSkipsPersist() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));

        PlatformSettingsResponse response = service.removeLogo("owner-1");

        assertThat(response.platform().logoUrl()).isNull();
        // The stored icon is still cleared — that is the one that was showing.
        verify(brandIconService).remove("owner-1");
        verify(repository, never()).save(any());
        verify(mediaUploadService, never()).deletePlatformBrandAsset(any());
    }

    @Test
    void branding_pointsEverySurfaceAtTheStoredIconWhenThereIsOne() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(brandIconService.exists()).thenReturn(true);

        // The stored icon wins over a legacy Cloudinary URL, so one upload
        // changes the tab icon, the installed app icon, the page chrome and the
        // sign-in pages together.
        assertThat(service.getBranding().logoUrl()).isEqualTo(STORED_ICON_PATH);
    }

    @Test
    void branding_fallsBackToALegacyCloudinaryUrl_thenToNothing() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(brandIconService.exists()).thenReturn(false);
        assertThat(service.getBranding().logoUrl()).isEqualTo(OLD_URL);

        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));
        // Nothing configured: every client falls back to the built-in mark.
        assertThat(service.getBranding().logoUrl()).isNull();
    }
}
