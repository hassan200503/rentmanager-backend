package com.rentmanager.ai.domain.service;

import com.rentmanager.ai.domain.service.AiBillingService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiBillingServiceImpl implements AiBillingService {

    @Override
    public void recordUsage(UUID tenantId, int tokensUsed) {
        // TODO SaaS billing logic (DB + event + analytics hook)
        System.out.println("Billing: tenant=" + tenantId + ", tokens=" + tokensUsed);
    }
}