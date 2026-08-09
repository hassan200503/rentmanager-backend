package com.rentmanager.modules.review.application;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Submits verified landlord reviews of renters (V65, bidirectional
 * ratings).
 *
 * <p>Verification rule mirrors the renter side: a review is only valid
 * from a landlord who has or had an active lease with the renter being
 * reviewed. The tenantId is always the authenticated landlord's account
 * (from the calling layer, never the request body); the lease is resolved
 * server-side from the landlord's own lease book, preferring the
 * currently active one.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenterReviewCommandService {

    /**
     * Statuses that prove a tenancy was actually active at some point.
     * DRAFT / PENDING_* / AWAITING_DEPOSIT / CANCELLED never count.
     */
    private static final EnumSet<LeaseStatus> VERIFIED_LEASE_STATUSES =
            EnumSet.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED,
                    LeaseStatus.EXPIRED, LeaseStatus.TERMINATED, LeaseStatus.SUSPENDED);

    private final RenterReviewRepository reviewRepository;
    private final LeaseRepository leaseRepository;

    @Transactional
    public RenterReview submit(
            UUID landlordTenantId,
            UUID tenantProfileId,
            int rating,
            String comment
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("Landlord tenant is required");
        }
        if (tenantProfileId == null) {
            throw new IllegalArgumentException("Renter profile is required");
        }

        UUID leaseId = findVerifiableLeaseId(landlordTenantId, tenantProfileId);

        reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "You have already reviewed this renter");
                });

        RenterReview review = RenterReview.submit(
                landlordTenantId, tenantProfileId, leaseId, rating, comment);

        RenterReview saved = reviewRepository.save(review);
        log.info("Renter review submitted. reviewId={} tenantId={} tenantProfileId={} rating={}",
                saved.getId(), landlordTenantId, tenantProfileId, rating);
        return saved;
    }

    /**
     * Any lease that proves a real tenancy (active, renewed, expired,
     * terminated or suspended) qualifies a landlord to review the renter —
     * preferring the currently active lease when one exists.
     */
    private UUID findVerifiableLeaseId(UUID landlordTenantId, UUID tenantProfileId) {
        List<Lease> leases = leaseRepository.findAllByTenant(landlordTenantId).stream()
                .filter(l -> l.getTenantProfileId() != null
                        && l.getTenantProfileId().equals(tenantProfileId))
                .toList();

        return leases.stream()
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE)
                .findFirst()
                .map(Lease::getId)
                .or(() -> leases.stream()
                        .filter(l -> VERIFIED_LEASE_STATUSES.contains(l.getStatus()))
                        .findFirst()
                        .map(Lease::getId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Only verified landlords with an active or past lease can review a renter"));
    }
}