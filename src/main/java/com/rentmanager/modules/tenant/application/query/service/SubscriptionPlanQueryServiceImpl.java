package com.rentmanager.modules.tenant.application.query.service;

import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionPlanQueryServiceImpl implements SubscriptionPlanQueryService {

    private final SubscriptionPlanRepository repository;

    public SubscriptionPlanQueryServiceImpl(SubscriptionPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<SubscriptionPlanResponse> getAll() {
        return repository.findAll().stream().map(this::map).toList();
    }

    @Override
    public SubscriptionPlanResponse getById(UUID id) {
        return map(repository.findById(id));
    }

    @Override
    public SubscriptionPlanResponse getByCode(String code) {
        return map(repository.findByCode(code));
    }

    private SubscriptionPlanResponse map(com.rentmanager.modules.tenant.domain.model.SubscriptionPlan plan) {
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
                plan.isActive()
        );
    }
}