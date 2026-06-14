package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.repository.AiUsageRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiUsageServiceImpl implements AiUsageService {

    private final AiUsageRepository aiUsageRepository;

    public AiUsageServiceImpl(AiUsageRepository aiUsageRepository) {
        this.aiUsageRepository = aiUsageRepository;
    }

    @Override
    public List<Object> getTenantUsage() {
        return aiUsageRepository.findByTenantId(null); // replace with tenant context later
    }
}