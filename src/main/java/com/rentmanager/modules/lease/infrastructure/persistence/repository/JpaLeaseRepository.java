package com.rentmanager.modules.lease.infrastructure.persistence.repository;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PURE JPA ACCESS LAYER
 *
 * RULES:
 * - NO business logic
 * - NO mapping
 * - ONLY persistence access
 * - Supports Specification-based querying
 */
public interface JpaLeaseRepository extends JpaRepository<LeaseEntity, UUID>,
        JpaSpecificationExecutor<LeaseEntity> {


    Optional<LeaseEntity> findByUnitIdAndStatus(UUID unitId, LeaseStatus status);

    boolean existsByUnitIdAndStatus(UUID unitId, LeaseStatus status);

    List<LeaseEntity> findAllByStatusAndStartDateLessThanEqual(
            LeaseStatus status,
            LocalDate date
    );

}