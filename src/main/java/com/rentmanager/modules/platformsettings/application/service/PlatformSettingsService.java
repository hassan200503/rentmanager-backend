package com.rentmanager.modules.platformsettings.application.service;

import com.rentmanager.modules.audit.domain.model.AuditLog;
import com.rentmanager.modules.audit.domain.service.AuditService;
import com.rentmanager.modules.platformsettings.api.dto.request.UpdatePlatformSettingsRequest;
import com.rentmanager.modules.platformsettings.api.dto.response.PlatformSettingsResponse;
import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Read/write surface for the platform singleton configuration.
 *
 * <p>Reads are open to every platform operator; writes are guarded at the
 * controller layer to ROLE_PLATFORM_OWNER. The effective values returned by
 * {@link #getEffectiveSettings()} drive the subscription expiry sweep and
 * the disbursement retry pipeline, so owner changes take effect on the next
 * scheduler pass with no redeploy.</p>
 */
@Slf4j
@Service
public class PlatformSettingsService {

    private static final int MIN_GRACE_DAYS = 1;
    private static final int MAX_GRACE_DAYS = 60;
    private static final int MIN_EXPIRY_MINUTES = 5;
    private static final int MAX_EXPIRY_MINUTES = 1440;
    private static final int MIN_MAX_RETRIES = 0;
    private static final int MAX_MAX_RETRIES = 10;

    private final PlatformSettingsRepository repository;
    private final AuditService auditService;
    private final String darajaBaseUrl;

    public PlatformSettingsService(
            PlatformSettingsRepository repository,
            AuditService auditService,
            @Value("${daraja.base-url:https://api.safaricom.co.ke}") String darajaBaseUrl
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.darajaBaseUrl = darajaBaseUrl;
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

    private void validate(UpdatePlatformSettingsRequest request) {
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

    private void recordAudit(PlatformSettings after, String actor) {
        try {
            auditService.record(new AuditLog(
                    null,
                    "PLATFORM_SETTINGS_UPDATE",
                    actor != null && !actor.isBlank() ? actor : "platform-owner",
                    "PLATFORM_OWNER",
                    "PLATFORM_SETTINGS",
                    PlatformSettings.SINGLETON_ID.toString(),
                    null,
                    "SUCCESS",
                    String.format(
                            "{\"premiumGraceDays\":%d,\"subscriptionExpiryMinutes\":%d,\"disbursementMaxRetries\":%d",
                            after.getPremiumGraceDays(),
                            after.getSubscriptionPaymentExpiryMinutes(),
                            after.getDisbursementMaxRetryAttempts()) + "}",
                    null,
                    null
            ));
        } catch (Exception e) {
            // Audit must never block the settings write.
            log.warn("Failed to record platform settings audit event. actor={}", actor, e);
        }
    }

    private PlatformSettingsResponse toResponse(PlatformSettings settings) {
        boolean sandbox = darajaBaseUrl != null && darajaBaseUrl.toLowerCase().contains("sandbox");
        return new PlatformSettingsResponse(
                new PlatformSettingsResponse.BillingSettings(
                        settings.getPremiumGraceDays(),
                        settings.getSubscriptionPaymentExpiryMinutes()),
                new PlatformSettingsResponse.DisbursementSettings(
                        settings.getDisbursementMaxRetryAttempts()),
                new PlatformSettingsResponse.RevenueSettings(
                        settings.getRevenueBusinessShortcode(),
                        settings.getRevenuePaybill(),
                        settings.getRevenueTill(),
                        settings.getRevenueB2CShortcode(),
                        settings.getRevenueMpesaPhone()),
                new PlatformSettingsResponse.PlatformInfo(
                        sandbox ? "SANDBOX" : "PRODUCTION",
                        sandbox,
                        settings.getSupportEmail(),
                        settings.getSupportPhone(),
                        settings.getUpdatedBy(),
                        settings.getUpdatedAt()));
    }
}