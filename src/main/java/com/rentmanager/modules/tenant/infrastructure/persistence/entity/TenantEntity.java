package com.rentmanager.modules.tenant.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    @Embedded
    private BrandingSettings brandingSettings = BrandingSettings.defaultSettings();

    // Per-landlord M-Pesa Daraja credentials. Defaults to unconfigured() so
    // every newly-persisted TenantEntity has a non-null embeddable, matching
    // the brandingSettings pattern immediately above. See DarajaCredentials
    // for why every field within it is individually encrypted at rest.
    @Setter
    @Embedded
    private DarajaCredentials darajaCredentials = DarajaCredentials.unconfigured();

    @Setter
    @Column(name = "tenant_code", nullable = false, unique = true, length = 50)
    private String tenantCode;

    @Setter
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Setter
    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Setter
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Setter
    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Setter
    @Column(name = "payout_phone_number", length = 20)
    private String payoutPhoneNumber;

    @Setter
    @Column(name = "address", length = 255)
    private String address;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TenantStatus status;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private TenantType type;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 50)
    private SubscriptionStatus subscriptionStatus;

    @Setter
    @Column(name = "organization_id")
    private UUID organizationId;


    @Setter
    @Column(unique = true, length = 255)
    private String clerkOrgId;


    @Setter
    @Column(name = "active_subscription_id")
    private UUID activeSubscriptionId;

    @Setter
    @Column(name = "timezone", length = 100)
    private String timezone;

    @Setter
    @Column(name = "currency", length = 20)
    private String currency;

    @Setter
    @Column(name = "locale", length = 20)
    private String locale;

    @Setter
    @Column(name = "active", nullable = false)
    private boolean active;

    @Setter
    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;




    @Setter
    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate;

    // ----------------------------------------------------------------
    // BILLING MODE (PHASE 1 DUAL REVENUE MODEL)
    // ----------------------------------------------------------------

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode", nullable = false, length = 50)
    private BillingMode billingMode;

    @Setter
    @Column(name = "subscription_plan_id")
    private UUID subscriptionPlanId;

    @Setter
    @Column(name = "plan_start_date")
    private LocalDate planStartDate;

    @Setter
    @Column(name = "plan_end_date")
    private LocalDate planEndDate;

    @Setter
    @Column(name = "plan_grace_ends_at")
    private LocalDate planGraceEndsAt;

    @Setter
    @Column(name = "plan_auto_renew", nullable = false)
    private boolean planAutoRenew;

}