package com.rentmanager.modules.tenant.application.mapper;

import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.modules.tenant.domain.model.Tenant;

public class TenantMapper {

    private TenantMapper() {
    }

    public static TenantResponse toResponse(Tenant tenant) {

        TenantResponse response = new TenantResponse();

        response.setTenantId(tenant.getId());
        response.setName(tenant.getName());
        response.setEmail(tenant.getEmail());
        response.setPhoneNumber(tenant.getPhoneNumber());

        // Address removed because Tenant domain does NOT contain it

        response.setStatus(
                tenant.getStatus() != null
                        ? tenant.getStatus().name()
                        : null
        );

        return response;
    }
}