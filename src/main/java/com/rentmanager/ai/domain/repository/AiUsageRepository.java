package com.rentmanager.ai.domain.repository;

import java.util.List;
import java.util.UUID;

public interface AiUsageRepository {

    List<Object> findByTenantId(UUID tenantId);
}