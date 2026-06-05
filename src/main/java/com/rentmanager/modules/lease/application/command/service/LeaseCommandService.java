package com.rentmanager.modules.lease.application.command.service;

import com.rentmanager.contract.lease.request.LeaseActionRequest;

import java.util.UUID;

public interface LeaseCommandService {

    void executeAction(UUID leaseId, LeaseActionRequest request);
}