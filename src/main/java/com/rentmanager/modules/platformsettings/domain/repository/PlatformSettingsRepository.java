package com.rentmanager.modules.platformsettings.domain.repository;

import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;

import java.util.Optional;

public interface PlatformSettingsRepository {

    Optional<PlatformSettings> findSingleton();

    PlatformSettings save(PlatformSettings settings);
}