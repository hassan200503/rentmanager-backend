package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.CommissionPolicyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommissionPolicyJpaRepository extends JpaRepository<CommissionPolicyJpaEntity, UUID> {

    Optional<CommissionPolicyJpaEntity> findByLandlordOrgIdIsNullAndActiveTrue();

    Optional<CommissionPolicyJpaEntity> findByLandlordOrgIdAndActiveTrue(UUID landlordOrgId);
}
