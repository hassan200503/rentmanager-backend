package com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.mapper;

import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.entity.AutoPaySettingsJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class AutoPaySettingsPersistenceMapper {

    public AutoPaySettingsJpaEntity toJpaEntity(AutoPaySettings settings) {
        if (settings == null) return null;

        AutoPaySettingsJpaEntity jpa = new AutoPaySettingsJpaEntity();
        jpa.setId(settings.getId());
        jpa.assignTenantIfUnset(settings.getTenantId());
        jpa.setVersion(settings.getVersion());
        jpa.setLeaseId(settings.getLeaseId());
        jpa.setTenantProfileId(settings.getTenantProfileId());
        jpa.setEnabled(settings.isEnabled());
        jpa.setMpesaPhone(settings.getMpesaPhone());
        jpa.setLastAutoPayDate(settings.getLastAutoPayDate());
        jpa.setConsecutiveFailures(settings.getConsecutiveFailures());
        jpa.setLastAttemptAt(settings.getLastAttemptAt());
        jpa.setLastFailureReason(settings.getLastFailureReason());
        return jpa;
    }

    public AutoPaySettings toDomain(AutoPaySettingsJpaEntity jpa) {
        if (jpa == null) return null;

        return AutoPaySettings.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getLeaseId(),
                jpa.getTenantProfileId(),
                jpa.isEnabled(),
                jpa.getMpesaPhone(),
                jpa.getLastAutoPayDate(),
                jpa.getConsecutiveFailures(),
                jpa.getLastAttemptAt(),
                jpa.getLastFailureReason(),
                jpa.getVersion()
        );
    }
}