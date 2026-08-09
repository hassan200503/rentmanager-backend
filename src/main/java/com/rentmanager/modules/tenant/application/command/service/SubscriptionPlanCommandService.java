package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.SubscriptionPlanRequest;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;

import java.util.UUID;

public interface SubscriptionPlanCommandService {

    SubscriptionPlanResponse create(SubscriptionPlanRequest request);

    SubscriptionPlanResponse update(UUID id, SubscriptionPlanRequest request);

    void deactivate(UUID id);
}