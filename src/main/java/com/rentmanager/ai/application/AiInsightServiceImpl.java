package com.rentmanager.ai.application;

import com.rentmanager.ai.application.AiInsightService;
import com.rentmanager.ai.domain.model.AiInsight;
import com.rentmanager.ai.domain.repository.AiInsightRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AiInsightServiceImpl implements AiInsightService {

    private final AiInsightRepository aiInsightRepository;

    public AiInsightServiceImpl(AiInsightRepository aiInsightRepository) {
        this.aiInsightRepository = aiInsightRepository;
    }

    /**
     * Tenant-scoped retrieval (SaaS isolation enforced via tenantId)
     */
    @Override
    public List<AiInsight> getTenantInsights() {
        UUID tenantId = resolveTenantId();
        return aiInsightRepository.findByTenantId(tenantId);
    }

    /**
     * Entity-scoped retrieval within tenant boundary
     */
    @Override
    public List<AiInsight> getInsightsByEntity(UUID entityId) {
        UUID tenantId = resolveTenantId();
        return aiInsightRepository.findByEntity(tenantId, entityId);
    }

    /**
     * Centralized tenant resolution hook (replace with your TenantContext later)
     */
    private UUID resolveTenantId() {
        // SaaS placeholder: MUST be replaced with TenantContextHolder or SecurityContext
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}