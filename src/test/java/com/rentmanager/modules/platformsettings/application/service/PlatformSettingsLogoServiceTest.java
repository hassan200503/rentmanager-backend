package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformBrandingResponse;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformSettingsResponse;
import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.shared.service.MediaUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * System-wide logo lifecycle: upload (upload → replace + purge of the old
 * asset), remove (clear + purge), idempotent remove, and the public
 * branding projection. Manual mocks, no Mockito extension — per repo
 * conventions each test sets up its own stubbings.
 */
class PlatformSettingsLogoServiceTest {

    private static final String OLD_URL = "https://res.cloudinary.com/rentmanager/image/upload/rentmanager/platform/branding/old_x7p2k9.png";
    private static final String NEW_URL = "https://res.cloudinary.com/rentmanager/image/upload/rentmanager/platform/branding/new_m4n8z1.png";

    private PlatformSettingsRepository repository;
    private AuditService auditService;
    private MediaUploadService mediaUploadService;
    private IntegrationRegistry integrationRegistry;
    private PlatformSettingsService service;

    @BeforeEach
    void setUp() {
        repository = mock(PlatformSettingsRepository.class);
        auditService = mock(AuditService.class);
        mediaUploadService = mock(MediaUploadService.class);
        integrationRegistry = mock(IntegrationRegistry.class);
        service = new PlatformSettingsService(
                repository, auditService, mediaUploadService,
                "https://sandbox.safaricom.co.ke", "RentManager", integrationRegistry);
    }

    private static MockMultipartFile png(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, "image/png", bytes);
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
    void uploadLogo_persistsNewUrlAndPurgesPreviousAsset() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(mediaUploadService.uploadPlatformBrandAsset(any())).thenReturn(NEW_URL);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSettingsResponse response = service.uploadLogo(
                png("logo.png", new byte[]{1, 2, 3}), "owner-1");

        assertThat(response.platform().logoUrl()).isEqualTo(NEW_URL);
        verify(mediaUploadService).deletePlatformBrandAsset(OLD_URL);
        ArgumentCaptor<PlatformSettings> saved = ArgumentCaptor.forClass(PlatformSettings.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLogoUrl()).isEqualTo(NEW_URL);
    }

    @Test
    void uploadLogo_whenNoPreviousLogo_doesNotAttemptPurge() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));
        when(mediaUploadService.uploadPlatformBrandAsset(any())).thenReturn(NEW_URL);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadLogo(png("logo.png", new byte[]{1, 2, 3}), "owner-1");

        verify(mediaUploadService, never()).deletePlatformBrandAsset(any());
    }

    @Test
    void uploadLogo_recordsBrandingAuditEvent() throws Exception {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(mediaUploadService.uploadPlatformBrandAsset(any())).thenReturn(NEW_URL);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.uploadLogo(png("logo.png", new byte[]{1, 2, 3}), "owner-1");

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
    void removeLogo_clearsUrlAndPurgesAsset() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(OLD_URL)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSettingsResponse response = service.removeLogo("owner-1");

        assertThat(response.platform().logoUrl()).isNull();
        verify(mediaUploadService).deletePlatformBrandAsset(OLD_URL);
        ArgumentCaptor<PlatformSettings> saved = ArgumentCaptor.forClass(PlatformSettings.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getLogoUrl()).isNull();
    }

    @Test
    void removeLogo_whenNoneConfigured_isIdempotentAndSkipsPersist() {
        when(repository.findSingleton()).thenReturn(Optional.of(settingsWithLogo(null)));

        PlatformSettingsResponse response = service.removeLogo("owner-1");

        assertThat(response.platform().logoUrl()).isNull();
        verify(repository, never()).save(any());
        verify(mediaUploadService, never()).deletePlatformBrandAsset(any());
    }

    @Test
    void getBranding_exposesOnlyPublicIdentity() {
        UUID ownerId = UUID.randomUUID();
        PlatformSettings settings = PlatformSettings.rehydrate(
                7, 30, 3, 30,
                null, null, null, null, null,
                "support@rentmanager.co.ke", "+254712345678",
                NEW_URL,
                ownerId.toString(),
                java.time.Instant.parse("2026-08-11T10:15:30Z"),
                1L
        );
        when(repository.findSingleton()).thenReturn(Optional.of(settings));

        PlatformBrandingResponse branding = service.getBranding();

        assertThat(branding.platformName()).isEqualTo("RentManager");
        assertThat(branding.logoUrl()).isEqualTo(NEW_URL);
        assertThat(branding.environment()).isEqualTo("SANDBOX");
        assertThat(branding.supportEmail()).isEqualTo("support@rentmanager.co.ke");
        assertThat(branding.supportPhone()).isEqualTo("+254712345678");
        assertThat(branding.updatedAt()).isNotNull();
        verify(repository, never()).save(any());
    }
}