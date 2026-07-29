package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;

import java.util.Optional;
import java.util.UUID;

public interface CommissionPolicyRepository {

    CommissionPolicy save(CommissionPolicy policy);

    Optional<CommissionPolicy> findById(UUID id);

    /**
     * Finds the active platform-wide default (landlord_org_id IS NULL).
     */
    Optional<CommissionPolicy> findActiveDefault();

    /**
     * Finds the active policy for a specific landlord override.
     */
    Optional<CommissionPolicy> findActiveByLandlordOrgId(UUID landlordOrgId);
}
