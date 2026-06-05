package com.rentmanager.modules.tenant.infrastructure.persistence.specification;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantSettingsEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;

public class TenantSettingsSpecification {

    // ------------------------------------------------------------
    // CAN UPDATE SETTINGS
    // ------------------------------------------------------------
    public static boolean canUpdateSettings(TenantEntity tenant, TenantSettingsEntity settings) {

        if (tenant == null || settings == null) {
            throw new IllegalArgumentException("Tenant and settings cannot be null");
        }

        // Block updates for deactivated tenants
        if (!tenant.isActive()) {
            return false;
        }

        return true;
    }

    // ------------------------------------------------------------
    // CAN CHANGE LIMITS (SAAS CONTROL RULE)
    // ------------------------------------------------------------
    public static boolean canChangeLimits(TenantEntity tenant) {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Only active tenants can change limits
        if (!tenant.isActive()) {
            return false;
        }

        // Optional: prevent changes if onboarding not complete
        if (!tenant.isOnboardingCompleted()) {
            return false;
        }

        return true;
    }

    // ------------------------------------------------------------
    // CAN ENABLE FEATURE FLAGS
    // ------------------------------------------------------------
    public static boolean canEnableFeatureFlags(TenantEntity tenant) {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Prevent unstable tenants from changing feature flags
        return tenant.isActive() && tenant.isOnboardingCompleted();
    }

    // ------------------------------------------------------------
    // CAN DISABLE CRITICAL FEATURES
    // ------------------------------------------------------------
    public static boolean canDisableCriticalFeatures(TenantEntity tenant) {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Allow disabling features even if suspended (for safety control)
        return !tenant.isActive() || tenant.isActive();
    }

    // ------------------------------------------------------------
    // CAN UPDATE LOCALIZATION SETTINGS
    // ------------------------------------------------------------
    public static boolean canUpdateLocalization(TenantEntity tenant) {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Localization is safe even for active tenants
        return tenant.isActive();
    }

    // ------------------------------------------------------------
    // CAN UPDATE SECURITY SETTINGS
    // ------------------------------------------------------------
    public static boolean canUpdateSecuritySettings(TenantEntity tenant) {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant cannot be null");
        }

        // Security changes require active + onboarded tenant
        return tenant.isActive() && tenant.isOnboardingCompleted();
    }

    // ------------------------------------------------------------
    // VALID SETTINGS SNAPSHOT
    // ------------------------------------------------------------
    public static boolean isValidSettingsSnapshot(TenantSettingsEntity settings) {

        if (settings == null) {
            return false;
        }

        // Basic SaaS validation rules

        if (settings.getMaxProperties() != null && settings.getMaxProperties() < 0) {
            return false;
        }

        if (settings.getMaxUnits() != null && settings.getMaxUnits() < 0) {
            return false;
        }

        if (settings.getMaxUsers() != null && settings.getMaxUsers() < 0) {
            return false;
        }

        return true;
    }
}