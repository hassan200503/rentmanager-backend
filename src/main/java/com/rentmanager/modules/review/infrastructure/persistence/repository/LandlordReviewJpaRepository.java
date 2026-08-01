package com.rentmanager.modules.review.infrastructure.persistence.repository;

import com.rentmanager.modules.review.infrastructure.persistence.entity.LandlordReviewJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LandlordReviewJpaRepository extends JpaRepository<LandlordReviewJpaEntity, UUID> {

    List<LandlordReviewJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID landlordTenantId);

    Optional<LandlordReviewJpaEntity> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId);

    long countByTenantId(UUID landlordTenantId);
}
