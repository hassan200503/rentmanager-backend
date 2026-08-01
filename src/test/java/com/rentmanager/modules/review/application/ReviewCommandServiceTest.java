package com.rentmanager.modules.review.application;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4b: verified-renter gate. A review is only valid from a renter
 * with an active or past lease; DRAFT / PENDING / CANCELLED never count.
 */
class ReviewCommandServiceTest {

    private LandlordReviewRepository reviewRepository;
    private LeaseRepository leaseRepository;
    private ReviewCommandService service;

    private final UUID landlordTenantId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(LandlordReviewRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        service = new ReviewCommandService(reviewRepository, leaseRepository);
    }

    @Test
    void submitsReview_whenRenterHasActiveLease() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(LandlordReview.class))).thenAnswer(inv -> inv.getArgument(0));

        LandlordReview saved = service.submit(landlordTenantId, tenantProfileId, leaseId, 5, "Great landlord");

        assertNotNull(saved);
        assertEquals(5, saved.getRating());
        assertEquals("Great landlord", saved.getComment());
        verify(reviewRepository).save(any(LandlordReview.class));
    }

    @Test
    void submitsReview_whenRenterHadExpiredLease() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.EXPIRED);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(LandlordReview.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.submit(landlordTenantId, tenantProfileId, leaseId, 4, null));
    }

    @Test
    void rejectsReview_whenLeaseNeverActive() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.CANCELLED);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, leaseId, 5, null));

        assertTrue(ex.getMessage().contains("verified"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsReview_whenLeaseBelongsToAnotherRenter() {
        Lease lease = mockLease(leaseId, UUID.randomUUID(), LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, leaseId, 5, null));
    }

    @Test
    void rejectsReview_whenLeaseIdNotMatched() {
        Lease lease = mockLease(UUID.randomUUID(), tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));

        assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, leaseId, 5, null));
    }

    @Test
    void rejectsDuplicateReviewPerRenter() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.of(LandlordReview.rehydrate(
                        UUID.randomUUID(), landlordTenantId, tenantProfileId, leaseId,
                        5, "Already reviewed", 0L, null, null)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, leaseId, 3, "Second"));

        assertTrue(ex.getMessage().contains("already"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsOutOfRangeRating() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, leaseId, 6, null));
    }

    private Lease mockLease(UUID id, UUID profileId, LeaseStatus status) {
        Lease lease = mock(Lease.class);
        when(lease.getId()).thenReturn(id);
        when(lease.getTenantProfileId()).thenReturn(profileId);
        when(lease.getStatus()).thenReturn(status);
        return lease;
    }
}
