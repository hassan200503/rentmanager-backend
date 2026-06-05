package com.rentmanager.modules.lease.application.query.projection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseProjectionRepository {

    Optional<LeaseProjection> findById(
            UUID tenantId,
            UUID leaseId
    );

    List<LeaseProjection> findByTenant(
            UUID tenantId
    );

    List<LeaseProjection> findByProperty(
            UUID tenantId,
            UUID propertyId
    );

    List<LeaseProjection> findByUnit(
            UUID tenantId,
            UUID unitId
    );
}