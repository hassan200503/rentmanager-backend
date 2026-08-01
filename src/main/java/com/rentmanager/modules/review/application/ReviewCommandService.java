package com.rentmanager.modules.review.application;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Submits verified renter reviews (Phase 4b).
 *
 * <p>Verification rule: a review is only valid from a renter who has or
 * had an active lease with the landlord. The tenantId is always the
 * landlord's account - resolved from the authenticated renter's profile
 * by the calling layer (TenantPortalService), never from the request
 * body, so cross-tenant submission is structurally impossible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCommandService {

    /**
     * Statuses that prove a tenancy was actually active at some point.
     * DRAFT / PENDING_* / AWAITING_DEPOSIT / CANCELLED never count.
     */
    private static final EnumSet<LeaseStatus> VERIFIED_LEASE_STATUSES =
            EnumSet.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED,
                    LeaseStatus.EXPIRED, LeaseStatus.TERMINATED, LeaseStatus.SUSPENDED);

    private final LandlordReviewRepository reviewRepository;
    private final LeaseRepository leaseRepository;

    @Transactional
    public LandlordReview submit(
            UUID landlordTenantId,
            UUID tenantProfileId,
            UUID leaseId,
            int rating,
            String comment
    ) {
        if (landlordTenantId == null) {
            throw new IllegalArgumentException("Landlord tenant is required");
        }
        if (tenantProfileId == null) {
            throw new IllegalArgumentException("Renter profile is required");
        }

        verifyRenterHasLease(landlordTenantId, tenantProfileId, leaseId);

        reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                            "You have already reviewed this landlord");
                });

        LandlordReview review = LandlordReview.submit(
                landlordTenantId, tenantProfileId, leaseId, rating, comment);

        LandlordReview saved = reviewRepository.save(review);
        log.info("Landlord review submitted. reviewId={} tenantId={} tenantProfileId={} rating={}",
                saved.getId(), landlordTenantId, tenantProfileId, rating);
        return saved;
    }

    private void verifyRenterHasLease(UUID landlordTenantId, UUID tenantProfileId, UUID leaseId) {
        if (leaseId == null) {
            throw new IllegalArgumentException("Lease is required to leave a review");
        }

        List<Lease> leases = leaseRepository.findAllByTenant(landlordTenantId);
        boolean verified = leases.stream()
                .filter(l -> l.getTenantProfileId() != null
                        && l.getTenantProfileId().equals(tenantProfileId))
                .filter(l -> l.getId().equals(leaseId))
                .anyMatch(l -> VERIFIED_LEASE_STATUSES.contains(l.getStatus()));

        if (!verified) {
            throw new IllegalArgumentException(
                    "Only verified renters with an active or past lease can review a landlord");
        }
    }
}
