package com.rentmanager.modules.tenant.domain.repository;



import com.rentmanager.modules.tenant.domain.model.TenantSettings;

import java.util.Optional;
import java.util.UUID;

public interface TenantSettingsRepository {

 Optional<TenantSettings> findByTenantId(UUID tenantId);

 TenantSettings save(TenantSettings settings);
}