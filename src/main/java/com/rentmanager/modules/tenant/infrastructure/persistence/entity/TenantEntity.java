package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "tenants",
        indexes = {
                @Index(name = "idx_tenant_code", columnList = "tenant_code"),
                @Index(name = "idx_tenant_slug", columnList = "slug"),
                @Index(name = "idx_tenant_status", columnList = "status")
        }
)
@NoArgsConstructor
public class TenantEntity extends BaseEntity {



    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TenantType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 50)
    private SubscriptionStatus subscriptionStatus;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "active_subscription_id")
    private UUID activeSubscriptionId;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "currency", length = 20)
    private String currency;

    @Column(name = "locale", length = 20)
    private String locale;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;




    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public void setType(TenantType type) {
        this.type = type;
    }

    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public void setActiveSubscriptionId(UUID activeSubscriptionId) {
        this.activeSubscriptionId = activeSubscriptionId;
    }


    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setOnboardingCompleted(boolean onboardingCompleted) {
        this.onboardingCompleted = onboardingCompleted;
    }
}