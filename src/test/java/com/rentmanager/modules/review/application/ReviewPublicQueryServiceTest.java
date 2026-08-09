package com.rentmanager.modules.review.application;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4b public read path: tenant id is resolved server-side from the
 * already-public unit/property id (never from the request), only for
 * publicly visible listings, and renter names are redacted to first names.
 */
class ReviewPublicQueryServiceTest {

    private ReviewQueryService reviewQueryService;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private ReviewPublicQueryService service;

    private final UUID landlordTenantId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewQueryService = mock(ReviewQueryService.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        service = new ReviewPublicQueryService(reviewQueryService, unitRepository, propertyRepository);
    }

    @Test
    void getForUnit_resolvesTenantFromPubliclyVisibleUnit() {
        Unit unit = mock(Unit.class);
        when(unit.getTenantId()).thenReturn(landlordTenantId);
        when(unitRepository.findPubliclyVisibleVacantUnitById(unitId))
                .thenReturn(Optional.of(unit));
        stubQueryService();

        service.getForUnit(unitId);

        verify(reviewQueryService).getSummary(landlordTenantId);
        verify(reviewQueryService).getApprovedReviews(landlordTenantId);
    }

    @Test
    void getForUnit_nonPublicUnit_throwsWithoutTouchingReviews() {
        when(unitRepository.findPubliclyVisibleVacantUnitById(unitId))
                .thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> service.getForUnit(unitId));
        verify(reviewQueryService, never()).getSummary(any());
    }

    @Test
    void getForProperty_resolvesTenantFromActiveProperty() {
        Property property = mock(Property.class);
        when(property.getTenantId()).thenReturn(landlordTenantId);
        when(propertyRepository.findByIdAndStatus(propertyId, PropertyStatus.ACTIVE))
                .thenReturn(Optional.of(property));
        stubQueryService();

        service.getForProperty(propertyId);

        verify(reviewQueryService).getSummary(landlordTenantId);
    }

    @Test
    void getForProperty_inactiveProperty_throwsWithoutTouchingReviews() {
        when(propertyRepository.findByIdAndStatus(propertyId, PropertyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> service.getForProperty(propertyId));
        verify(reviewQueryService, never()).getSummary(any());
    }

    @Test
    void redactsRenterNamesToFirstName() {
        Unit unit = mockUnit();
        when(unitRepository.findPubliclyVisibleVacantUnitById(unitId))
                .thenReturn(Optional.of(unit));
        when(reviewQueryService.getSummary(landlordTenantId))
                .thenReturn(new ReviewSummaryResponse(2, null, false));
        when(reviewQueryService.getApprovedReviews(landlordTenantId))
                .thenReturn(List.of(
                        new LandlordReviewResponse(UUID.randomUUID(), "Wanjiku Mwangi", 5, "Excellent", ReviewStatus.APPROVED, Instant.now()),
                        new LandlordReviewResponse(UUID.randomUUID(), "Brian", 4, "Good", ReviewStatus.APPROVED, Instant.now())));

        var result = service.getForUnit(unitId);

        assertEquals(2, result.reviewCount());
        assertEquals("Wanjiku", result.reviews().get(0).renterName());
        assertEquals("Brian", result.reviews().get(1).renterName());
        assertEquals(5, result.reviews().get(0).rating());
    }

    @Test
    void passesSummaryHonestyRuleThrough() {
        Unit unit = mockUnit();
        when(unitRepository.findPubliclyVisibleVacantUnitById(unitId))
                .thenReturn(Optional.of(unit));
        when(reviewQueryService.getSummary(landlordTenantId))
                .thenReturn(new ReviewSummaryResponse(2, null, false));
        when(reviewQueryService.getApprovedReviews(landlordTenantId)).thenReturn(List.of());

        var result = service.getForUnit(unitId);

        assertNull(result.averageRating());
        assertEquals(false, result.averageShown());
    }

    private void stubQueryService() {
        when(reviewQueryService.getSummary(landlordTenantId))
                .thenReturn(new ReviewSummaryResponse(0, null, false));
        when(reviewQueryService.getApprovedReviews(landlordTenantId)).thenReturn(List.of());
    }

    private Unit mockUnit() {
        Unit unit = mock(Unit.class);
        when(unit.getTenantId()).thenReturn(landlordTenantId);
        return unit;
    }
}
