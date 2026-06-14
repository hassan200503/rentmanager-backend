package com.rentmanager.ai.infrastructure.persistence;

import com.rentmanager.ai.domain.model.AiInsight;
import com.rentmanager.ai.domain.repository.AiInsightRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("dev")
public class InMemoryAiInsightRepository implements AiInsightRepository {

    private final Map<UUID, AiInsight> store = new ConcurrentHashMap<>();

    @Override
    public void save(String tenantId, String prompt, String response) {

    }

    @Override
    public AiInsight save(AiInsight insight) {
        store.put(insight.getId(), insight);
        return insight;
    }

    @Override
    public List<AiInsight> findByTenantId(UUID tenantId) {
        return store.values()
                .stream()
                .filter(i -> i.getTenantId().equals(tenantId))
                .toList();
    }

    @Override
    public List<AiInsight> findByEntity(UUID tenantId, UUID entityId) {
        return store.values()
                .stream()
                .filter(i -> i.getTenantId().equals(tenantId))
                .toList();
    }
}