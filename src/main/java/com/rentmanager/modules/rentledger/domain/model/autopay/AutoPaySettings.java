package com.rentmanager.modules.rentledger.domain.model.autopay;

import com.rentmanager.domain.base.AggregateRoot;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-lease auto-pay configuration. There is exactly one row per lease
 * (enforced by a unique constraint on lease_id). When enabled, the daily
 * AutoPayScheduler picks up the lease and initiates an STK push to the
 * stored M-Pesa phone number on the due date of each outstanding entry.
 *
 * DESIGN DECISIONS:
 *
 * 1. Consecutive-failures cap at 3, after which the system auto-disables
 *    itself to prevent repeated failed charges. Tenant must re-enable
 *    manually (or after resolving the payment issue). This mirrors the
 *    behaviour of card-on-file retry policies in subscription billing.
 *
 * 2. mpesa_phone is stored separately from TenantProfile.phone because
 *    the tenant might want auto-pay debited from a different number than
 *    their primary contact phone (e.g. a business line vs personal line).
 *
 * 3. No shadowed version/createdAt/updatedAt fields — inherited from
 *    BaseEntity via AggregateRoot. The rehydrate factory uses the
 *    inherited setters.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AutoPaySettings extends AggregateRoot {

    private UUID leaseId;
    private UUID tenantProfileId;
    private boolean enabled;
    private String mpesaPhone;
    private LocalDate lastAutoPayDate;
    private int consecutiveFailures;
    private LocalDateTime lastAttemptAt;

    // -------------------------------------------------------
    // FACTORY
    // -------------------------------------------------------

    public static AutoPaySettings create(
            UUID tenantId,
            UUID leaseId,
            UUID tenantProfileId,
            String mpesaPhone
    ) {
        AutoPaySettings settings = AutoPaySettings.builder()
                .leaseId(leaseId)
                .tenantProfileId(tenantProfileId)
                .enabled(false)
                .mpesaPhone(mpesaPhone)
                .consecutiveFailures(0)
                .build();

        settings.assignTenant(tenantId);
        return settings;
    }

    // -------------------------------------------------------
    // BEHAVIOUR
    // -------------------------------------------------------

    public void enable() {
        this.enabled = true;
        this.consecutiveFailures = 0;
    }

    public void disable() {
        this.enabled = false;
    }

    public void updatePhone(String mpesaPhone) {
        if (mpesaPhone == null || mpesaPhone.isBlank()) {
            throw new IllegalArgumentException("mpesaPhone is required");
        }
        this.mpesaPhone = mpesaPhone;
    }

    /**
     * Called after a successful auto-pay STK push. Resets the failure
     * counter and records the date.
     */
    public void recordSuccess() {
        this.lastAutoPayDate = LocalDate.now();
        this.consecutiveFailures = 0;
        this.lastAttemptAt = LocalDateTime.now();
    }

    /**
     * Called after a failed auto-pay attempt. Increments the failure
     * counter. If the counter reaches 3, auto-disables.
     */
    public void recordFailure() {
        this.consecutiveFailures++;
        this.lastAttemptAt = LocalDateTime.now();
        if (this.consecutiveFailures >= 3) {
            this.enabled = false;
        }
    }

    /**
     * Returns true if the system should retry (fewer than 3 consecutive
     * failures). Once the threshold is reached, the setting auto-disables
     * and the tenant must re-enable manually.
     */
    public boolean shouldRetry() {
        return this.consecutiveFailures < 3;
    }

    // -------------------------------------------------------
    // REHYDRATION
    // -------------------------------------------------------

    public static AutoPaySettings rehydrate(
            UUID id,
            UUID tenantId,
            UUID leaseId,
            UUID tenantProfileId,
            boolean enabled,
            String mpesaPhone,
            LocalDate lastAutoPayDate,
            int consecutiveFailures,
            LocalDateTime lastAttemptAt,
            Long version
    ) {
        AutoPaySettings settings = AutoPaySettings.builder()
                .leaseId(leaseId)
                .tenantProfileId(tenantProfileId)
                .enabled(enabled)
                .mpesaPhone(mpesaPhone)
                .lastAutoPayDate(lastAutoPayDate)
                .consecutiveFailures(consecutiveFailures)
                .lastAttemptAt(lastAttemptAt)
                .build();

        settings.setId(id);
        settings.assignTenant(tenantId);
        settings.setVersion(version);

        return settings;
    }
}