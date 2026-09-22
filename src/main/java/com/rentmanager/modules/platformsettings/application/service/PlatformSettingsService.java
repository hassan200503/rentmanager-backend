package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.integration.application.IntegrationRegistry;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.model.ProviderCatalog;
import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import com.rentmanager.modules.platformsettings.api.dto.request.UpdatePlatformSettingsRequest;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformBrandingResponse;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformSettingsResponse;
import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.shared.exception.BusinessException;
import com.rentmanager.shared.service.MediaUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

/**
 * Read/write surface for the platform singleton configuration.
 *
 * <p>Reads are open to every platform operator; writes are guarded at the
 * controller layer to ROLE_PLATFORM_OWNER. The effective values returned by
 * {@link #getEffectiveSettings()} drive the subscription expiry sweep and
 * the disbursement retry pipeline, so owner changes take effect on the next
 * scheduler pass with no redeploy.</p>
 *
 * <p>System-wide branding (the platform logo) is owned by the dedicated
 * multi-part endpoints ({@link #uploadLogo} / {@link #removeLogo}) and is
 * served unauthenticated through {@link #getBranding()} for every chrome
 * surface and email template.</p>
 */
@Slf4j
@Service
public class PlatformSettingsService {

    private static final int MIN_TRIAL_DAYS = 7;
    private static final int MAX_TRIAL_DAYS = 90;
    private static final int MIN_GRACE_DAYS = 1;
    private static final int MAX_GRACE_DAYS = 60;
    private static final int MIN_EXPIRY_MINUTES = 5;
    private static final int MAX_EXPIRY_MINUTES = 1440;
    private static final int MIN_MAX_RETRIES = 0;
    private static final int MAX_MAX_RETRIES = 10;

    private final PlatformSettingsRepository repository;
    private final AuditService auditService;
    private final MediaUploadService mediaUploadService;
    private final PlatformBrandIconService brandIconService;
    private final String darajaBaseUrl;
    private final String brandingName;
    private final IntegrationRegistry integrationRegistry;

    public PlatformSettingsService(
            PlatformSettingsRepository repository,
            AuditService auditService,
            MediaUploadService mediaUploadService,
            PlatformBrandIconService brandIconService,
            @Value("${daraja.base-url:https://api.safaricom.co.ke}") String darajaBaseUrl,
            @Value("${platform.branding-name:RentManager}") String brandingName,
            IntegrationRegistry integrationRegistry
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.mediaUploadService = mediaUploadService;
        this.brandIconService = brandIconService;
        this.darajaBaseUrl = darajaBaseUrl;
        this.brandingName = brandingName;
        this.integrationRegistry = integrationRegistry;
    }

    @Transactional(readOnly = true)
    public PlatformSettingsResponse get() {
        return toResponse(getEffectiveSettings());
    }

    /**
     * The live config consumed by the schedulers. Falls back to the domain
     * defaults (mirror of the migration seed) if the row is ever missing.
     */
    @Transactional(readOnly = true)
    public PlatformSettings getEffectiveSettings() {
        return repository.findSingleton()
                .orElseGet(() -> PlatformSettings.defaults("config-defaults"));
    }

    @Transactional
    public PlatformSettingsResponse update(UpdatePlatformSettingsRequest request, String actor) {
        validate(request);

        PlatformSettings current = getEffectiveSettings();
        PlatformSettings updated = current.reconfigure(
                request.premiumGraceDays(),
                request.subscriptionPaymentExpiryMinutes(),
                request.disbursementMaxRetryAttempts(),
                request.trialDurationDays(),
                request.revenueBusinessShortcode(),
                request.revenuePaybill(),
                request.revenueTill(),
                request.revenueB2CShortcode(),
                request.revenueMpesaPhone(),
                request.supportEmail(),
                request.supportPhone(),
                actor == null || actor.isBlank() ? "platform-owner" : actor
        );

        PlatformSettings saved = repository.save(updated);
        recordAudit(saved, actor);

        log.info("Platform owner updated platform settings: actor={} premiumGraceDays={} " +
                        "subscriptionExpiryMinutes={} disbursementMaxRetries={}",
                actor, saved.getPremiumGraceDays(),
                saved.getSubscriptionPaymentExpiryMinutes(), saved.getDisbursementMaxRetryAttempts());

        return toResponse(saved);
    }

    /**
     * Replaces the platform icon. The bytes go into our own database
     * (V104), not Cloudinary: on a deployment with no Cloudinary credentials
     * — the free-tier default — the old Cloudinary upload failed, so the owner
     * could not change the icon at all. One upload now drives every surface
     * that reads GET /public/platform/branding.
     */
    @Transactional
    public PlatformSettingsResponse uploadLogo(MultipartFile file, String actor) {
        String resolvedActor = resolveActor(actor);

        brandIconService.upload(file, resolvedActor);

        PlatformSettings current = getEffectiveSettings();
        String legacyCloudinaryUrl = current.getLogoUrl();

        // Clear the legacy URL so there is one answer to "which icon is
        // current?", and purge the old asset best-effort.
        PlatformSettings saved = current;
        if (hasLogo(legacyCloudinaryUrl)) {
            saved = repository.save(current.withLogo(null, resolvedActor));
            mediaUploadService.deletePlatformBrandAsset(legacyCloudinaryUrl);
        }

        recordAudit("PLATFORM_BRANDING_UPDATE", "{\"action\":\"logo_uploaded\"}", resolvedActor);
        log.info("Platform owner uploaded system-wide logo: actor={}", resolvedActor);

        return toResponse(saved);
    }

    @Transactional
    public PlatformSettingsResponse removeLogo(String actor) {
        String resolvedActor = resolveActor(actor);
        PlatformSettings current = getEffectiveSettings();

        brandIconService.remove(resolvedActor);

        if (!hasLogo(current.getLogoUrl())) {
            // Idempotent — no legacy asset to purge; every surface falls back
            // to the built-in mark now the stored icon is gone.
            recordAudit("PLATFORM_BRANDING_UPDATE", "{\"action\":\"logo_removed\"}", resolvedActor);
            return toResponse(current);
        }

        String previousUrl = current.getLogoUrl();
        PlatformSettings updated = current.withLogo(null, resolvedActor);
        PlatformSettings saved = repository.save(updated);

        mediaUploadService.deletePlatformBrandAsset(previousUrl);

        recordAudit("PLATFORM_BRANDING_UPDATE", "{\"action\":\"logo_removed\"}", resolvedActor);
        log.info("Platform owner removed system-wide logo: actor={}", resolvedActor);

        return toResponse(saved);
    }

    /**
     * Minimal public identity consumed unauthenticated by every branding
     * surface (console, shells, landing page, favicon, email templates).
     * Deliberately exposes nothing operational.
     */
    @Transactional(readOnly = true)
    public PlatformBrandingResponse getBranding() {
        PlatformSettings settings = getEffectiveSettings();
        // A stored icon wins over a legacy Cloudinary URL. The path is
        // relative so it works through the web app's same-origin proxy and
        // straight against the API alike.
        //
        // The ?v= stamp is what makes an upload visible. This used to be a
        // fixed path, so replacing the icon changed the bytes but not the
        // address -- and no cache between this database and the browser tab
        // had any reason to ask for it again. Favicons in particular are held
        // far longer than any header suggests. Stamping the stored timestamp
        // onto the URL turns each upload into a new resource, which every
        // cache already knows how to handle correctly.
        String logoUrl = brandIconService.currentVersion()
                .map(v -> "/api/v1/public/platform/branding/logo?v=" + v.toEpochMilli())
                .orElseGet(settings::getLogoUrl);
        return new PlatformBrandingResponse(
                brandingName,
                logoUrl,
                isSandbox() ? "SANDBOX" : "PRODUCTION",
                settings.getSupportEmail(),
                settings.getSupportPhone(),
                settings.getUpdatedAt());
    }

    private void validate(UpdatePlatformSettingsRequest request) {
        if (request.trialDurationDays() < MIN_TRIAL_DAYS || request.trialDurationDays() > MAX_TRIAL_DAYS) {
            throw new BusinessException(
                    "trialDurationDays must be between " + MIN_TRIAL_DAYS + " and " + MAX_TRIAL_DAYS,
                    com.rentmanager.shared.exception.ErrorCode.VALIDATION_ERROR);
        }
        if (request.premiumGraceDays() < MIN_GRACE_DAYS || request.premiumGraceDays() > MAX_GRACE_DAYS) {
            throw new BusinessException(
                    "premiumGraceDays must be between " + MIN_GRACE_DAYS + " and " + MAX_GRACE_DAYS,
                    com.rentmanager.shared.exception.ErrorCode.VALIDATION_ERROR);
        }
        if (request.subscriptionPaymentExpiryMinutes() < MIN_EXPIRY_MINUTES
                || request.subscriptionPaymentExpiryMinutes() > MAX_EXPIRY_MINUTES) {
            throw new BusinessException(
                    "subscriptionPaymentExpiryMinutes must be between " + MIN_EXPIRY_MINUTES
                            + " and " + MAX_EXPIRY_MINUTES,
                    com.rentmanager.shared.exception.ErrorCode.VALIDATION_ERROR);
        }
        if (request.disbursementMaxRetryAttempts() < MIN_MAX_RETRIES
                || request.disbursementMaxRetryAttempts() > MAX_MAX_RETRIES) {
            throw new BusinessException(
                    "disbursementMaxRetryAttempts must be between " + MIN_MAX_RETRIES
                            + " and " + MAX_MAX_RETRIES,
                    com.rentmanager.shared.exception.ErrorCode.VALIDATION_ERROR);
        }
    }

    private String resolveActor(String actor) {
        return actor == null || actor.isBlank() ? "platform-owner" : actor;
    }

    private static boolean hasLogo(String url) {
        return url != null && !url.isBlank();
    }

    private boolean isSandbox() {
        // The active Daraja environment is the source of truth for the
        // deployment-tier badge (per-provider Development/Production model).
        // Falls back to the legacy env-derived base URL while nothing is
        // activated in the console.
        IntegrationEnvironment active = integrationRegistry.activeEnvironment(ProviderCatalog.DARAJA);
        if (active != null) {
            return active == IntegrationEnvironment.DEVELOPMENT;
        }
        return darajaBaseUrl != null && darajaBaseUrl.toLowerCase().contains("sandbox");
    }

    private void recordAudit(String eventType, String details, String actor) {
        try {
            auditService.record(new AuditLog(
                    null,
                    eventType,
                    actor,
                    "PLATFORM_OWNER",
                    "PLATFORM_SETTINGS",
                    PlatformSettings.SINGLETON_ID.toString(),
                    null,
                    "SUCCESS",
                    details,
                    null,
                    null
            ));
        } catch (Exception e) {
            // Audit must never block the settings write.
            log.warn("Failed to record platform settings audit event. eventType={} actor={}", eventType, actor, e);
        }
    }

    private void recordAudit(PlatformSettings after, String actor) {
        recordAudit(
                "PLATFORM_SETTINGS_UPDATE",
                String.format(
                        "{\"premiumGraceDays\":%d,\"subscriptionExpiryMinutes\":%d,\"disbursementMaxRetries\":%d",
                        after.getPremiumGraceDays(),
                        after.getSubscriptionPaymentExpiryMinutes(),
                        after.getDisbursementMaxRetryAttempts()) + "}",
                resolveActor(actor)
        );
    }

    private PlatformSettingsResponse toResponse(PlatformSettings settings) {
        return new PlatformSettingsResponse(
                new PlatformSettingsResponse.BillingSettings(
                        settings.getPremiumGraceDays(),
                        settings.getSubscriptionPaymentExpiryMinutes(),
                        settings.getTrialDurationDays()),
                new PlatformSettingsResponse.DisbursementSettings(
                        settings.getDisbursementMaxRetryAttempts()),
                new PlatformSettingsResponse.RevenueSettings(
                        settings.getRevenueBusinessShortcode(),
                        settings.getRevenuePaybill(),
                        settings.getRevenueTill(),
                        settings.getRevenueB2CShortcode(),
                        settings.getRevenueMpesaPhone()),
                new PlatformSettingsResponse.PlatformInfo(
                        isSandbox() ? "SANDBOX" : "PRODUCTION",
                        isSandbox(),
                        settings.getSupportEmail(),
                        settings.getSupportPhone(),
                        settings.getLogoUrl(),
                        settings.getUpdatedBy(),
                        settings.getUpdatedAt()));
    }
}