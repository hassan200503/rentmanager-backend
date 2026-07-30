package com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.adapter;

import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.domain.repository.autopay.AutoPaySettingsRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.entity.AutoPaySettingsJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.mapper.AutoPaySettingsPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.repository.AutoPaySettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AutoPaySettingsRepositoryAdapter implements AutoPaySettingsRepository {

    private final AutoPaySettingsJpaRepository jpaRepository;
    private final AutoPaySettingsPersistenceMapper mapper;

    @Override
    public AutoPaySettings save(AutoPaySettings settings) {
        AutoPaySettingsJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(settings));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<AutoPaySettings> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<AutoPaySettings> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId) {
        return jpaRepository.findByLeaseIdAndTenantId(leaseId, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<AutoPaySettings> findAllByEnabledTrue() {
        return jpaRepository.findAllByEnabledTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AutoPaySettings> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}