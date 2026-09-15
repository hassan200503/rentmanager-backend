package com.rentmanager.modules.platformsettings.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "platform_settings")
public class PlatformSettingsEntity extends BaseEntity {

    @Column(name = "updated_by", nullable = false, length = 200)
    private String updatedBy;

    @Column(name = "premium_grace_days", nullable = false)
    private int premiumGraceDays;

    @Column(name = "subscription_payment_expiry_minutes", nullable = false)
    private int subscriptionPaymentExpiryMinutes;

    @Column(name = "disbursement_max_retry_attempts", nullable = false)
    private int disbursementMaxRetryAttempts;

    @Column(name = "revenue_business_shortcode", length = 20)
    private String revenueBusinessShortcode;

    @Column(name = "revenue_paybill", length = 20)
    private String revenuePaybill;

    @Column(name = "revenue_till", length = 20)
    private String revenueTill;

    @Column(name = "revenue_b2c_shortcode", length = 20)
    private String revenueB2CShortcode;

    @Column(name = "revenue_mpesa_phone", length = 20)
    private String revenueMpesaPhone;

    @Column(name = "support_email", length = 150)
    private String supportEmail;

    @Column(name = "support_phone", length = 20)
    private String supportPhone;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "trial_duration_days", nullable = false)
    private int trialDurationDays;
}