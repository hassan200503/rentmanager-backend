package com.rentmanager.modules.lease.application.dto.response;

import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;

import java.util.UUID;

public record LeaseActionResponse(
        UUID id,
        LeaseStatusDTO previousStatus,
        LeaseStatusDTO currentStatus,
        String message
) {}