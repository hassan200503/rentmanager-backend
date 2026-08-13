package com.rentmanager.modules.integration.infrastructure.persistence.mapper;

import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationConfigEntity;
import org.springframework.stereotype.Component;

@Component
public class IntegrationConfigPersistenceMapper {

    public IntegrationConfig toDomain(IntegrationConfigEntity entity) {
        return new IntegrationConfig(
                entity.getId(),
                entity.getProviderKey(),
                entity.getEnvironment(),
                entity.isActive(),
                entity.getEncryptedCredentials(),
                entity.getKeyVersion(),
                entity.getStatus(),
                entity.getLastVerifiedAt(),
                entity.getLastVerifiedBy(),
                entity.getLastError(),
                entity.getUpdatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public IntegrationConfigEntity toEntity(IntegrationConfig config) {
        IntegrationConfigEntity entity = new IntegrationConfigEntity();
        entity.restoreId(config.getId());
        entity.setProviderKey(config.getProviderKey());
        entity.setEnvironment(config.getEnvironment());
        entity.setActive(config.isActive());
        entity.setEncryptedCredentials(config.getEncryptedCredentials());
        entity.setKeyVersion(config.getKeyVersion());
        entity.setStatus(config.getStatus());
        entity.setLastVerifiedAt(config.getLastVerifiedAt());
        entity.setLastVerifiedBy(config.getLastVerifiedBy());
        entity.setLastError(config.getLastError());
        entity.setUpdatedBy(config.getUpdatedBy());
        entity.restoreCreatedAt(config.getCreatedAt());
        entity.restoreUpdatedAt(config.getUpdatedAt());
        entity.setVersion(config.getVersion());
        return entity;
    }
}