package com.rentmanager.modules.tenant.application.query.projection;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;

import java.util.UUID;

public class TenantProjection {

    private UUID tenantId;
    private String tenantCode;
    private String name;
    private String slug;
    private String email;
    private String phoneNumber;

    private TenantStatus status;
    private TenantType type;
    private SubscriptionStatus subscriptionStatus;

    private boolean active;
    private boolean onboardingCompleted;

    public TenantProjection(
            UUID tenantId,
            String tenantCode,
            String name,
            String slug,
            String email,
            String phoneNumber,
            TenantStatus status,
            TenantType type,
            SubscriptionStatus subscriptionStatus,
            boolean active,
            boolean onboardingCompleted
    ) {
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.name = name;
        this.slug = slug;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.type = type;
        this.subscriptionStatus = subscriptionStatus;
        this.active = active;
        this.onboardingCompleted = onboardingCompleted;
    }

    // getters only
}