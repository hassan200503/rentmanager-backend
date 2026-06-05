package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenant_settings",
        indexes = {
                @Index(name = "idx_settings_tenant_id", columnList = "tenant_id")
        }
)
@NoArgsConstructor
public class TenantSettingsEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    // ----------------------------------------------------------------
    // LOCALIZATION SETTINGS
    // ----------------------------------------------------------------

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "currency", length = 20)
    private String currency;

    @Column(name = "locale", length = 20)
    private String locale;

    // ----------------------------------------------------------------
    // FEATURE FLAGS
    // ----------------------------------------------------------------

    @Column(name = "enable_notifications", nullable = false)
    private boolean enableNotifications;

    @Column(name = "enable_audit_logs", nullable = false)
    private boolean enableAuditLogs;

    @Column(name = "enable_multi_branch", nullable = false)
    private boolean enableMultiBranch;

    // ----------------------------------------------------------------
    // BUSINESS RULE SETTINGS
    // ----------------------------------------------------------------

    @Column(name = "auto_approve_tenants", nullable = false)
    private boolean autoApproveTenants;

    @Column(name = "require_identity_verification", nullable = false)
    private boolean requireIdentityVerification;

    @Column(name = "allow_public_signup", nullable = false)
    private boolean allowPublicSignup;

    // ----------------------------------------------------------------
    // LIMIT SETTINGS (SAAS CONTROL)
    // ----------------------------------------------------------------

    @Column(name = "max_properties")
    private Integer maxProperties;

    @Column(name = "max_units")
    private Integer maxUnits;

    @Column(name = "max_users")
    private Integer maxUsers;

    // ----------------------------------------------------------------
    // SECURITY SETTINGS
    // ----------------------------------------------------------------

    @Column(name = "password_policy_strength", length = 50)
    private String passwordPolicyStrength;

    @Column(name = "session_timeout_minutes")
    private Integer sessionTimeoutMinutes;

    @Column(name = "enable_two_factor_auth", nullable = false)
    private boolean enableTwoFactorAuth;
}