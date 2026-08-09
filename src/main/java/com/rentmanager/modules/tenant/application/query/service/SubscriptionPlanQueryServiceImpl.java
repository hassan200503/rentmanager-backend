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
    public List<SubscriptionPlanResponse> getActive() {
        return repository.findAllActive().stream().map(this::map).toList();
    }

    @Override
    public SubscriptionPlanResponse getById(UUID id) {
        return map(repository.findById(id).orElseThrow(() ->
                new com.rentmanager.shared.exception.ResourceNotFoundException(
                        "Subscription plan not found: " + id,
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                )));
    }

    @Override
    public SubscriptionPlanResponse getByCode(String code) {
        return map(repository.findByCode(code).orElseThrow(() ->
                new com.rentmanager.shared.exception.ResourceNotFoundException(
                        "Subscription plan not found: " + code,
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                )));
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
                plan.isActive(),
                plan.isSelfService()
        );
    }
}