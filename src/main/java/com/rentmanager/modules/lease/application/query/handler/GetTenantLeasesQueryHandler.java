package com.rentmanager.modules.lease.application.query.handler;

import com.rentmanager.modules.lease.application.query.projection.LeaseProjection;
import com.rentmanager.modules.lease.application.query.service.LeaseQueryService;

import java.util.List;
import java.util.UUID;

/**
 * Handles tenant lease listing queries.
 */
public class GetTenantLeasesQueryHandler {

    private final LeaseQueryService service;

    public GetTenantLeasesQueryHandler(LeaseQueryService service) {
        this.service = service;
    }

    public List<LeaseProjection> handle(UUID tenantId) {
        return service.getLeasesByTenant(tenantId);
    }
}