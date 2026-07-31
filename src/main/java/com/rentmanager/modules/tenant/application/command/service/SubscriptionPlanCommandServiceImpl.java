package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.SubscriptionPlanRequest;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;


import com.rentmanager.modules.tenant.domain.enums.BillingCycle;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SubscriptionPlanCommandServiceImpl implements SubscriptionPlanCommandService {

    private final SubscriptionPlanRepository repository;

    public SubscriptionPlanCommandServiceImpl(SubscriptionPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public SubscriptionPlanResponse create(SubscriptionPlanRequest request) {

        SubscriptionPlan plan = SubscriptionPlan.reconstruct(
                request.getCode(),
                request.getName(),
                request.getDescription(),
                request.getBillingCycle(),
                request.getMaxProperties(),
                request.getMaxUnits(),
                request.getMaxUsers(),
                request.getMaxStorageGb(),
                request.getMonthlyPrice(),
                request.getYearlyPrice(),
                true,
                true
        );

        SubscriptionPlan saved = repository.save(plan);

        return map(saved);
    }

    @Override
    public void deactivate(UUID id) {
        SubscriptionPlan plan = repository.findById(id)
                .orElseThrow(() -> new com.rentmanager.shared.exception.ResourceNotFoundException(
                        "Subscription plan not found: " + id,
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                ));
        plan.deactivate();
        repository.save(plan);
    }

    private SubscriptionPlanResponse map(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getBillingCycle(),
                plan.getMaxProperties(),
                plan.getMaxUnits(),
                plan.getMaxUsers(),
                plan.getMaxStorageGb(),
                plan.getMonthlyPrice(),
                plan.getYearlyPrice(),
                plan.isActive(),
                plan.isSelfService()
        );
    }
}