package com.rentmanager.modules.platformsettings.infrastructure.persistence.mapper;

import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.infrastructure.persistence.entity.PlatformSettingsEntity;
import org.springframework.stereotype.Component;

@Component
public class PlatformSettingsPersistenceMapper {

    public PlatformSettingsEntity toJpaEntity(PlatformSettings settings) {
        if (settings == null) {
            return null;
        }

        PlatformSettingsEntity jpa = new PlatformSettingsEntity();
        jpa.setId(PlatformSettings.SINGLETON_ID);
        jpa.setVersion(settings.getVersion());
        jpa.setUpdatedBy(settings.getUpdatedBy());
        jpa.setPremiumGraceDays(settings.getPremiumGraceDays());
        jpa.setSubscriptionPaymentExpiryMinutes(settings.getSubscriptionPaymentExpiryMinutes());
        jpa.setDisbursementMaxRetryAttempts(settings.getDisbursementMaxRetryAttempts());
        jpa.setRevenueBusinessShortcode(settings.getRevenueBusinessShortcode());
        jpa.setRevenuePaybill(settings.getRevenuePaybill());
        jpa.setRevenueTill(settings.getRevenueTill());
        jpa.setRevenueB2CShortcode(settings.getRevenueB2CShortcode());
        jpa.setRevenueMpesaPhone(settings.getRevenueMpesaPhone());
        jpa.setSupportEmail(settings.getSupportEmail());
        jpa.setSupportPhone(settings.getSupportPhone());
        return jpa;
    }

    public PlatformSettings toDomain(PlatformSettingsEntity jpa) {
        if (jpa == null) {
            return null;
        }

        return PlatformSettings.rehydrate(
                jpa.getPremiumGraceDays(),
                jpa.getSubscriptionPaymentExpiryMinutes(),
                jpa.getDisbursementMaxRetryAttempts(),
                jpa.getRevenueBusinessShortcode(),
                jpa.getRevenuePaybill(),
                jpa.getRevenueTill(),
                jpa.getRevenueB2CShortcode(),
                jpa.getRevenueMpesaPhone(),
                jpa.getSupportEmail(),
                jpa.getSupportPhone(),
                jpa.getUpdatedBy(),
                jpa.getUpdatedAt(),
                jpa.getVersion()
        );
    }
}