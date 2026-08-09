package com.rentmanager.modules.review.infrastructure.persistence.adapter;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import com.rentmanager.modules.review.infrastructure.persistence.entity.LandlordReviewJpaEntity;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.LandlordReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.repository.LandlordReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LandlordReviewRepositoryAdapter implements LandlordReviewRepository {

    private final LandlordReviewJpaRepository jpaRepository;
    private final LandlordReviewPersistenceMapper mapper;

    @Override
    public LandlordReview save(LandlordReview review) {
        LandlordReviewJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(review));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<LandlordReview> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<LandlordReview> findByTenantId(UUID landlordTenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(landlordTenantId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<LandlordReview> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId) {
        return jpaRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .map(mapper::toDomain);
    }

    @Override
    public long countByTenantId(UUID landlordTenantId) {
        return jpaRepository.countByTenantId(landlordTenantId);
    }

    @Override
    public List<LandlordReview> findByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(landlordTenantId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status) {
        return jpaRepository.countByTenantIdAndStatus(landlordTenantId, status);
    }

    @Override
    public List<LandlordReview> findApprovedByTenantId(UUID landlordTenantId) {
        return findByTenantIdAndStatus(landlordTenantId, ReviewStatus.APPROVED);
    }

    @Override
    public long countApprovedByTenantId(UUID landlordTenantId) {
        return jpaRepository.countByTenantIdAndStatus(landlordTenantId, ReviewStatus.APPROVED);
    }
}