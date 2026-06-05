package com.rentmanager.modules.tenant.domain.repository;

import com.rentmanager.modules.tenant.domain.model.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganisationRepository {

    Organization save(Organization organization);

    Optional<Organization> findById(UUID id);

    Optional<Organization> findByTenantId(UUID tenantId);
}