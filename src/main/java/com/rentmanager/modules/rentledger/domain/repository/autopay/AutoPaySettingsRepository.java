package com.rentmanager.modules.rentledger.domain.repository.autopay;

import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutoPaySettingsRepository {

    AutoPaySettings save(AutoPaySettings settings);

    Optional<AutoPaySettings> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AutoPaySettings> findByLeaseIdAndTenantId(UUID leaseId, UUID tenantId);

    List<AutoPaySettings> findAllByEnabledTrue();

    List<AutoPaySettings> findAllByTenantId(UUID tenantId);

    void delete(UUID id);
}