package com.rentmanager.ai.infrastructure.persistence;

import com.rentmanager.ai.domain.repository.AiUsageRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class InMemoryAiUsageRepository implements AiUsageRepository {

    @Override
    public List<Object> findByTenantId(UUID tenantId) {
        return List.of(); // stub for now
    }
}