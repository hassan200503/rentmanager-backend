package com.rentmanager.modules.integration.infrastructure.persistence.adapter;

import com.rentmanager.modules.integration.domain.model.IntegrationConfig;
import com.rentmanager.modules.integration.domain.model.IntegrationEnvironment;
import com.rentmanager.modules.integration.domain.repository.IntegrationConfigRepository;
import com.rentmanager.modules.integration.infrastructure.persistence.entity.IntegrationConfigEntity;
import com.rentmanager.modules.integration.infrastructure.persistence.mapper.IntegrationConfigPersistenceMapper;
import com.rentmanager.modules.integration.infrastructure.persistence.repository.IntegrationConfigJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IntegrationConfigRepositoryAdapter implements IntegrationConfigRepository {

    private final IntegrationConfigJpaRepository jpaRepository;
    private final IntegrationConfigPersistenceMapper mapper;

    @Override
    public Optional<IntegrationConfig> find(String providerKey, IntegrationEnvironment environment) {
        return jpaRepository
                .findByProviderKeyAndEnvironment(providerKey, environment)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<IntegrationConfig> findActive(String providerKey) {
        return jpaRepository
                .findByProviderKeyAndActiveTrue(providerKey)
                .map(mapper::toDomain);
    }

    @Override
    public List<IntegrationConfig> findAll(String providerKey) {
        return jpaRepository.findByProviderKeyOrderByEnvironmentAsc(providerKey).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<IntegrationConfig> findAllActive() {
        return jpaRepository.findByActiveTrue().stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public IntegrationConfig save(IntegrationConfig config) {
        IntegrationConfigEntity saved = jpaRepository.save(mapper.toEntity(config));
        return mapper.toDomain(saved);
    }

    @Override
    public void deactivateAll(String providerKey, IntegrationEnvironment except) {
        jpaRepository.deactivateOthers(providerKey, except);
    }

    @Override
    public Optional<IntegrationConfig> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
}