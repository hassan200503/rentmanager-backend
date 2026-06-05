package com.rentmanager.modules.lease.application.command.usecase;

import com.rentmanager.contract.lease.request.LeaseActionRequest;
import com.rentmanager.modules.lease.application.command.handler.LeaseActionHandler;
import com.rentmanager.modules.lease.application.command.validator.LeaseActionValidator;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LeaseActionUseCase {

    private final LeaseActionValidator validator;
    private final LeaseActionHandler handler;

    public LeaseActionUseCase(
            LeaseActionValidator validator,
            LeaseActionHandler handler
    ) {
        this.validator = validator;
        this.handler = handler;
    }

    // ✅ FIXED: leaseId must be passed from controller
    public void execute(UUID leaseId, LeaseActionRequest request) {

        // 1. validate input (pure DTO validation)
        validator.validate(request);

        // 2. execute business workflow
        handler.handle(leaseId, request);
    }
}