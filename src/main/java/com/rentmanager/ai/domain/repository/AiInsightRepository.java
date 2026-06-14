package com.rentmanager.ai.domain.repository;

import com.rentmanager.ai.domain.model.AiInsight;

import java.util.List;
import java.util.UUID;

public interface AiInsightRepository {

    void save(String tenantId, String prompt, String response);

    AiInsight save(AiInsight insight);

    List<AiInsight> findByTenantId(UUID tenantId);

    List<AiInsight> findByEntity(UUID tenantId, UUID entityId);
}