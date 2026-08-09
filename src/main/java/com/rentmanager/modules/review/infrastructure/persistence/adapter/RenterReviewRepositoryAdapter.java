package com.rentmanager.modules.review.infrastructure.persistence.adapter;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import com.rentmanager.modules.review.infrastructure.persistence.entity.RenterReviewJpaEntity;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.RenterReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.repository.RenterReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RenterReviewRepositoryAdapter implements RenterReviewRepository {

    private final RenterReviewJpaRepository jpaRepository;
    private final RenterReviewPersistenceMapper mapper;

    @Override
    public RenterReview save(RenterReview review) {
        RenterReviewJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(review));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RenterReview> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<RenterReview> findByTenantId(UUID landlordTenantId) {
        return jpaRepository.findByTenantIdOrderByCreatedAtDesc(landlordTenantId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RenterReview> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId) {
        return jpaRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .map(mapper::toDomain);
    }

    @Override
    public List<RenterReview> findByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status) {
        return jpaRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(landlordTenantId, status)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status) {
        return jpaRepository.countByTenantIdAndStatus(landlordTenantId, status);
    }

    @Override
    public List<RenterReview> findApprovedByTenantId(UUID landlordTenantId) {
        return findByTenantIdAndStatus(landlordTenantId, ReviewStatus.APPROVED);
    }

    @Override
    public long countApprovedByTenantId(UUID landlordTenantId) {
        return jpaRepository.countByTenantIdAndStatus(landlordTenantId, ReviewStatus.APPROVED);
    }
}