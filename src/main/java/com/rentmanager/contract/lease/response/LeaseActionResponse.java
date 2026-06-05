package com.rentmanager.contract.lease.response;

import com.rentmanager.contract.lease.dto.LeaseStatusDTO;

import java.util.UUID;

public record LeaseActionResponse(
        UUID id,
        LeaseStatusDTO previousStatus,
        LeaseStatusDTO currentStatus,
        String message
) {}