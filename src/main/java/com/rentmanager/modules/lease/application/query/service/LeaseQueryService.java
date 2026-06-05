package com.rentmanager.modules.lease.application.query.service;

import com.rentmanager.modules.lease.application.query.projection.LeaseProjection;
import com.rentmanager.modules.lease.application.query.projection.LeaseProjectionRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
/**
 * SaaS-grade query service for Lease read operations.
 */
@Service
public class LeaseQueryService {

    private final LeaseProjectionRepository repository;

    public LeaseQueryService(LeaseProjectionRepository repository) {
        this.repository = repository;
    }

    public LeaseProjection getLease(UUID tenantId, UUID leaseId) {
        return repository.findById(tenantId, leaseId)
                .orElseThrow(() -> new IllegalArgumentException("Lease not found"));
    }

    public List<LeaseProjection> getLeasesByTenant(UUID tenantId) {
        return repository.findByTenant(tenantId);
    }

    public List<LeaseProjection> getLeasesByProperty(UUID tenantId, UUID propertyId) {
        return repository.findByProperty(tenantId, propertyId);
    }

    public List<LeaseProjection> getLeasesByUnit(UUID tenantId, UUID unitId) {
        return repository.findByUnit(tenantId, unitId);
    }
}