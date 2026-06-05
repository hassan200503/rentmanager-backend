package com.rentmanager.modules.tenant.application.dto.request;

import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateTenantRequest {

    private String tenantCode;
    private String name;
    private String slug; // ✅ FIXED MISSING FIELD
    private String email;
    private String phoneNumber;

    private TenantType tenantType;
    private SubscriptionStatus subscriptionStatus; // optional (used in overload)
}