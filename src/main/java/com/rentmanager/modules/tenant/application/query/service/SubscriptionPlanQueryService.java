package com.rentmanager.modules.tenant.application.query.service;

import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;

import java.util.List;
import java.util.UUID;

public interface SubscriptionPlanQueryService {

    List<SubscriptionPlanResponse> getAll();

    SubscriptionPlanResponse getById(UUID id);

    SubscriptionPlanResponse getByCode(String code);
}