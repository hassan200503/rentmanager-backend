package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.model.AiInsight;

import java.util.List;
import java.util.UUID;

public interface AiInsightService {

    List<AiInsight> getTenantInsights();

    List<AiInsight> getInsightsByEntity(UUID entityId);
}