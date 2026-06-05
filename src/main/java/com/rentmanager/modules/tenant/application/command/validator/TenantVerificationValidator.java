package com.rentmanager.modules.tenant.application.command.validator;

import java.util.UUID;

public interface TenantVerificationValidator {

    void validateVerification(UUID tenantId);

    void validateApproval(UUID tenantId);

    void validateRejection(UUID tenantId);

}