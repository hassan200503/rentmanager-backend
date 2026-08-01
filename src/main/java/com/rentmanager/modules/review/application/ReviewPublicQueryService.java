package com.rentmanager.modules.review.application;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.PublicLandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.PublicLandlordReviewsResponse;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Public read side for landlord reviews (Phase 4b), used by the public
 * property/unit listing pages. The landlord tenant id is NEVER taken from
 * the request - it is resolved server-side from the already-public
 * property/unit id, and only for listings that are themselves publicly
 * visible (active property + active, vacant unit), so private listings
 * never leak review data.
 *
 * <p>Privacy: renter names are redacted to the first name only on public
 * routes - a full name is personal data that only the landlord dashboard
 * may see.</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewPublicQueryService {

    private final ReviewQueryService reviewQueryService;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

    @Transactional(readOnly = true)
    public PublicLandlordReviewsResponse getForUnit(UUID unitId) {
        Unit unit = unitRepository.findPubliclyVisibleVacantUnitById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit not found", ErrorCode.UNIT_NOT_FOUND));
        return getLandlordReviews(unit.getTenantId());
    }

    @Transactional(readOnly = true)
    public PublicLandlordReviewsResponse getForProperty(UUID propertyId) {
        Property property = propertyRepository.findByIdAndStatus(propertyId, PropertyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found", ErrorCode.PROPERTY_NOT_FOUND));
        return getLandlordReviews(property.getTenantId());
    }

    private PublicLandlordReviewsResponse getLandlordReviews(UUID landlordTenantId) {
        var summary = reviewQueryService.getSummary(landlordTenantId);
        List<PublicLandlordReviewResponse> reviews = reviewQueryService
                .getReviews(landlordTenantId)
                .stream()
                .map(ReviewPublicQueryService::toPublicReview)
                .toList();
        return new PublicLandlordReviewsResponse(
                summary.reviewCount(),
                summary.averageRating(),
                summary.averageShown(),
                reviews
        );
    }

    private static PublicLandlordReviewResponse toPublicReview(LandlordReviewResponse review) {
        String firstName = null;
        if (review.renterName() != null && !review.renterName().isBlank()) {
            firstName = review.renterName().trim().split("\\s+")[0];
        }
        return new PublicLandlordReviewResponse(
                review.rating(),
                firstName,
                review.comment(),
                review.createdAt()
        );
    }
}
