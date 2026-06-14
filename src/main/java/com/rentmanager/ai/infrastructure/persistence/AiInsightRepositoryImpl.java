package com.rentmanager.ai.infrastructure.persistence;

import com.rentmanager.ai.domain.model.AiInsight;
import com.rentmanager.ai.domain.repository.AiInsightRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class AiInsightRepositoryImpl implements AiInsightRepository {

    private final JpaAiInsightRepository jpaRepo;

    public AiInsightRepositoryImpl(JpaAiInsightRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    public void save(String tenantId, String prompt, String response) {
        AiInsightEntity entity = new AiInsightEntity(
                tenantId,
                prompt,
                response,
                "qwen2.5",
                0L,
                java.time.Instant.now()
        );

        jpaRepo.save(entity);
    }

    @Override
    public AiInsight save(AiInsight insight) {
        return null;
    }

    @Override
    public List<AiInsight> findByTenantId(UUID tenantId) {
        return List.of();
    }

    @Override
    public List<AiInsight> findByEntity(UUID tenantId, UUID entityId) {
        return List.of();
    }
}