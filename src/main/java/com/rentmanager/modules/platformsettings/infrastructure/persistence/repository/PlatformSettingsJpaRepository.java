package com.rentmanager.modules.platformsettings.infrastructure.persistence.repository;

import com.rentmanager.modules.platformsettings.infrastructure.persistence.entity.PlatformSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformSettingsJpaRepository extends JpaRepository<PlatformSettingsEntity, UUID> {
}