package com.rentmanager.modules.integration.domain.repository;

import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegrationConfigRepository {

    Optional<IntegrationConfig> find(String providerKey, IntegrationEnvironment environment);

    Optional<IntegrationConfig> findActive(String providerKey);

    List<IntegrationConfig> findAll(String providerKey);

    List<IntegrationConfig> findAllActive();

    IntegrationConfig save(IntegrationConfig config);

    void deactivateAll(String providerKey, IntegrationEnvironment except);

    Optional<IntegrationConfig> findById(UUID id);
}