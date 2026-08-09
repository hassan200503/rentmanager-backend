package com.rentmanager.modules.review.application;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V65: the landlord -> renter review gate mirrors the renter side — a
 * review is only valid from a landlord with an active or past lease for
 * that renter profile; DRAFT / PENDING / CANCELLED never count.
 */
class RenterReviewCommandServiceTest {

    private RenterReviewRepository reviewRepository;
    private LeaseRepository leaseRepository;
    private RenterReviewCommandService service;

    private final UUID landlordTenantId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(RenterReviewRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        service = new RenterReviewCommandService(reviewRepository, leaseRepository);
    }

    @Test
    void submitsReview_whenLandlordHasActiveLease() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(RenterReview.class))).thenAnswer(inv -> inv.getArgument(0));

        RenterReview saved = service.submit(landlordTenantId, tenantProfileId, 5, "Reliable tenant");

        assertNotNull(saved);
        assertEquals(5, saved.getRating());
        assertEquals("Reliable tenant", saved.getComment());
        assertEquals(leaseId, saved.getLeaseId());
        verify(reviewRepository).save(any(RenterReview.class));
    }

    @Test
    void submitsReview_whenLandlordHadExpiredLease() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.EXPIRED);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(RenterReview.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNotNull(service.submit(landlordTenantId, tenantProfileId, 4, null));
    }

    @Test
    void rejectsReviewWithoutVerifiedLease() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.CANCELLED);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, 5, null));

        assertTrue(ex.getMessage().contains("verified"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsReview_whenProfileNeverRentedFromLandlord() {
        Lease lease = mockLease(leaseId, UUID.randomUUID(), LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, 5, null));

        assertTrue(ex.getMessage().contains("lease"));
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateReviewPerRenter() {
        Lease lease = mockLease(leaseId, tenantProfileId, LeaseStatus.ACTIVE);
        when(leaseRepository.findAllByTenant(landlordTenantId)).thenReturn(List.of(lease));
        when(reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId))
                .thenReturn(Optional.of(RenterReview.rehydrate(
                        UUID.randomUUID(), landlordTenantId, tenantProfileId, leaseId,
                        5, "Already reviewed", 0L, null, null)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submit(landlordTenantId, tenantProfileId, 3, "Second"));

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
                () -> service.submit(landlordTenantId, tenantProfileId, 6, null));
    }

    private Lease mockLease(UUID id, UUID profileId, LeaseStatus status) {
        Lease lease = mock(Lease.class);
        when(lease.getId()).thenReturn(id);
        when(lease.getTenantProfileId()).thenReturn(profileId);
        when(lease.getStatus()).thenReturn(status);
        return lease;
    }
}