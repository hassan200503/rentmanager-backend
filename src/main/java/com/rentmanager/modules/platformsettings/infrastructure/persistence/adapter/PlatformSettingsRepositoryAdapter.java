package com.rentmanager.modules.platformsettings.infrastructure.persistence.adapter;

import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.modules.platformsettings.infrastructure.persistence.entity.PlatformSettingsEntity;
import com.rentmanager.modules.platformsettings.infrastructure.persistence.mapper.PlatformSettingsPersistenceMapper;
import com.rentmanager.modules.platformsettings.infrastructure.persistence.repository.PlatformSettingsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PlatformSettingsRepositoryAdapter implements PlatformSettingsRepository {

    private final PlatformSettingsJpaRepository jpaRepository;
    private final PlatformSettingsPersistenceMapper mapper;

    @Override
    public Optional<PlatformSettings> findSingleton() {
        return jpaRepository.findById(PlatformSettings.SINGLETON_ID).map(mapper::toDomain);
    }

    @Override
    public PlatformSettings save(PlatformSettings settings) {
        PlatformSettingsEntity entity = mapper.toJpaEntity(settings);
        // saveAndFlush forces @PrePersist/@PreUpdate so the returned
        // entity always carries a fresh updated_at for the response DTO.
        return mapper.toDomain(jpaRepository.saveAndFlush(entity));
    }
}