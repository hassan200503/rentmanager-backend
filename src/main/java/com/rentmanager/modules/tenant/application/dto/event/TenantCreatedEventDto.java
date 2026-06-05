package com.rentmanager.modules.tenant.application.dto.event;

import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;

public class TenantCreatedEventDto extends BaseTenantEventDto {

    private String tenantCode;
    private String name;
    private String slug;
    private String email;
    private String phoneNumber;
    private TenantType tenantType;
    private SubscriptionStatus subscriptionStatus;

    public TenantCreatedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId,
            String tenantCode,
            String name,
            String slug,
            String email,
            String phoneNumber,
            TenantType tenantType,
            SubscriptionStatus subscriptionStatus
    ) {
        super(tenantId, targetTenantId);
        this.tenantCode = tenantCode;
        this.name = name;
        this.slug = slug;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.tenantType = tenantType;
        this.subscriptionStatus = subscriptionStatus;
    }

    // getters
}