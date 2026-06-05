package com.rentmanager.modules.tenant.application.command.validator;

import com.rentmanager.modules.tenant.application.dto.request.AssignTenantOwnerRequest;

import java.util.UUID;

public interface TenantOwnerValidator {

    void validateAssignOwner(UUID tenantId, AssignTenantOwnerRequest request);

    void validateChangeOwner(UUID tenantId, AssignTenantOwnerRequest request);

}