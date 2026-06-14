package com.rentmanager.ai.domain.service;

import java.util.UUID;

public interface AiBillingService {

    void recordUsage(UUID tenantId, int tokensUsed);
}