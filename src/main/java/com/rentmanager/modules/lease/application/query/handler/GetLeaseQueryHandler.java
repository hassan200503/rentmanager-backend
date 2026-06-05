package com.rentmanager.modules.lease.application.query.handler;

import com.rentmanager.modules.lease.application.query.projection.LeaseProjection;
import com.rentmanager.modules.lease.application.query.service.LeaseQueryService;

import java.util.UUID;

/**
 * Handles single lease query.
 */
public class GetLeaseQueryHandler {

    private final LeaseQueryService service;

    public GetLeaseQueryHandler(LeaseQueryService service) {
        this.service = service;
    }

    public LeaseProjection handle(UUID tenantId, UUID leaseId) {
        return service.getLease(tenantId, leaseId);
    }
}