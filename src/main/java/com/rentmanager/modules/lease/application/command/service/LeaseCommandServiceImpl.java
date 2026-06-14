package com.rentmanager.modules.lease.application.command.service;

import com.rentmanager.modules.lease.application.dto.request.LeaseActionRequest;
import com.rentmanager.modules.lease.application.command.usecase.LeaseActionUseCase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LeaseCommandServiceImpl implements LeaseCommandService {

    private final LeaseActionUseCase useCase;

    public LeaseCommandServiceImpl(LeaseActionUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executeAction(UUID leaseId, LeaseActionRequest request) {
        useCase.execute(leaseId, request);
    }
}