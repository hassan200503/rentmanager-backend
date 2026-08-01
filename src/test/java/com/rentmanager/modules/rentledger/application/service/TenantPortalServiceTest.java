package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.application.autopay.AutoPayService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code TenantPortalService}.
 *
 * Key findings from the trace phase:
 *   - {@code TenantPortalController} has NO {@code @PreAuthorize} annotations.
 *     Protection is purely identity-based: the controller passes the
 *     authenticated userId to the service, which resolves the renter's
 *     TenantProfile via the chain: userId → User → clerkUserId → TenantProfile.
 *   - Cross-renter isolation is therefore a SERVICE-layer concern, not a
 *     controller RBAC concern.
 *
 * Tests verify:
 *   1. Entry ownership check: initiateRentPayment() rejects a ledger entry
 *      that doesn't belong to the calling renter's active lease.
 *   2. Cross-lease check (positive + negative): the entry's leaseId must
 *      match the renter's active lease.
 *   3. Identity resolution: a userId with no matching User → exception.
 *      A User with no TenantProfile → exception.
 *   4. Tenant isolation: getPaymentRequestStatus() uses findByIdAndTenantId
 *      and throws when no record exists for that tenant.
 */
class TenantPortalServiceTest {

    private UserRepository userRepository;
    private TenantProfileRepository tenantProfileRepository;
    private LeaseRepository leaseRepository;
    private UnitRepository unitRepository;
    private PropertyRepository propertyRepository;
    private TenantRepository tenantRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentTransactionRepository rentTransactionRepository;
    private RentPaymentInitiationService rentPaymentInitiationService;
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    private AutoPayService autoPayService;
    private ReviewCommandService reviewCommandService;
    private ReviewQueryService reviewQueryService;
    private MaintenanceRequestCommandService maintenanceRequestCommandService;
    private MaintenanceRequestRepository maintenanceRequestRepository;

    private TenantPortalService service;

    // Test fixtures
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LANDLORD_TENANT_ID = UUID.randomUUID();
    private static final String CLERK_USER_ID = "clerk_" + UUID.randomUUID();

    private User renterUser;
    private TenantProfile tenantProfile;
    private Lease activeLease;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        unitRepository = mock(UnitRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        tenantRepository = mock(TenantRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentTransactionRepository = mock(RentTransactionRepository.class);
        rentPaymentInitiationService = mock(RentPaymentInitiationService.class);
        rentPaymentRequestRepository = mock(RentPaymentRequestRepository.class);
        autoPayService = mock(AutoPayService.class);
        reviewCommandService = mock(ReviewCommandService.class);
        reviewQueryService = mock(ReviewQueryService.class);
        maintenanceRequestCommandService = mock(MaintenanceRequestCommandService.class);
        maintenanceRequestRepository = mock(MaintenanceRequestRepository.class);

        service = new TenantPortalService(
                userRepository,
                tenantProfileRepository,
                leaseRepository,
                unitRepository,
                propertyRepository,
                tenantRepository,
                rentLedgerEntryRepository,
                rentTransactionRepository,
                rentPaymentInitiationService,
                rentPaymentRequestRepository,
                autoPayService,
                reviewCommandService,
                reviewQueryService,
                maintenanceRequestCommandService,
                maintenanceRequestRepository
        );

        renterUser = buildUser(USER_ID, CLERK_USER_ID, LANDLORD_TENANT_ID);
        tenantProfile = buildTenantProfile(LANDLORD_TENANT_ID, CLERK_USER_ID);
        activeLease = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(renterUser));
        when(tenantProfileRepository.findByClerkUserId(CLERK_USER_ID))
                .thenReturn(Optional.of(tenantProfile));
        when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                .thenReturn(List.of(activeLease));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Identity resolution
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class IdentityResolution {

        @Test
        void unknownUserId_throwsRentLedgerStateException() {
            UUID unknownUserId = UUID.randomUUID();
            when(userRepository.findById(unknownUserId)).thenReturn(Optional.empty());

            assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(unknownUserId, UUID.randomUUID(), "+254712345678"));
        }

        @Test
        void userWithNoTenantProfile_throwsRentLedgerStateException() {
            String clerkId = "clerk_no_profile";
            User userWithNoProfile = buildUser(UUID.randomUUID(), clerkId, LANDLORD_TENANT_ID);

            when(userRepository.findById(userWithNoProfile.getId()))
                    .thenReturn(Optional.of(userWithNoProfile));
            when(tenantProfileRepository.findByClerkUserId(clerkId))
                    .thenReturn(Optional.empty());

            assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(
                            userWithNoProfile.getId(), UUID.randomUUID(), "+254712345678"));
        }

        @Test
        void userWithNoActiveLease_throwsRentLedgerStateException() {
            // All leases are EXPIRED — no ACTIVE lease for this renter
            Lease expiredLease = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());
            expiredLease.expire(); // no re-approve — it's already active

            when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                    .thenReturn(List.of(expiredLease));

            assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(USER_ID, UUID.randomUUID(), "+254712345678"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Entry ownership / cross-lease check
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class EntryOwnershipCheck {

        /**
         * Positive case: entry belongs to the renter's active lease → proceeds.
         */
        @Test
        void initiateRentPayment_entryBelongsToActiveLease_proceeds() {
            UUID entryId = UUID.randomUUID();
            RentLedgerEntry entry = buildDueEntry(entryId, LANDLORD_TENANT_ID, activeLease.getId());

            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(entry));
            when(rentPaymentInitiationService.initiate(any(), any(), any()))
                    .thenReturn(buildDummyPaymentRequest());

            assertDoesNotThrow(() -> service.initiateRentPayment(USER_ID, entryId, "+254712345678"));
        }

        /**
         * Negative case: entry belongs to a DIFFERENT lease (not the renter's
         * active one) → must be rejected. This is the cross-lease check the
         * BUILD_REPORT.md claims exists — proved here.
         */
        @Test
        void initiateRentPayment_entryBelongsToDifferentLease_throwsException() {
            UUID entryId = UUID.randomUUID();
            UUID differentLeaseId = UUID.randomUUID(); // NOT the active lease's ID
            RentLedgerEntry entry = buildDueEntry(entryId, LANDLORD_TENANT_ID, differentLeaseId);

            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(entry));

            RentLedgerStateException ex = assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(USER_ID, entryId, "+254712345678"));

            // Service must reject, not proceed to payment initiation
            verifyNoInteractions(rentPaymentInitiationService);
            // Error message confirms the check
            assertTrue(ex.getMessage().contains("active lease"),
                    "Exception message should mention 'active lease': " + ex.getMessage());
        }

        /**
         * Entry not found for this tenant (cross-tenant IDOR attempt or
         * simply a missing entry) → must be rejected.
         */
        @Test
        void initiateRentPayment_entryNotFoundForTenant_throwsException() {
            UUID entryId = UUID.randomUUID();
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(USER_ID, entryId, "+254712345678"));

            verifyNoInteractions(rentPaymentInitiationService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Payment request status scoping
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class PaymentRequestStatusScoping {

        /**
         * getPaymentRequestStatus() uses findByIdAndTenantId — confirmed by
         * code read. This test proves it throws when the ID exists but belongs
         * to a different tenant (scoped to the calling renter's landlord tenantId).
         */
        @Test
        void getPaymentRequestStatus_requestBelongsToOtherTenant_throwsException() {
            UUID requestId = UUID.randomUUID();

            // Returns empty — the request doesn't belong to this tenant
            when(rentPaymentRequestRepository.findByIdAndTenantId(requestId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThrows(RentLedgerStateException.class,
                    () -> service.getPaymentRequestStatus(USER_ID, requestId));
        }

        @Test
        void getPaymentRequestStatus_requestBelongsToRenter_returnsResponse() {
            UUID requestId = UUID.randomUUID();
            RentPaymentRequest req = buildDummyPaymentRequest();

            when(rentPaymentRequestRepository.findByIdAndTenantId(requestId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(req));

            assertDoesNotThrow(() -> service.getPaymentRequestStatus(USER_ID, requestId));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Cross-renter isolation (identity-based)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class CrossRenterIsolation {

        /**
         * Renter B cannot access Renter A's data because the TenantProfile
         * resolved from Renter B's userId is different, which in turn resolves
         * a different activeLease — and Renter A's ledger entries are scoped to
         * Renter A's leaseId, so they won't match Renter B's lease.
         *
         * This proves that two different users authenticated in the same landlord
         * account can't cross-access each other's entries.
         */
        @Test
        void renterB_cannotAccessRenterAs_entry_viaIdentityResolution() {
            // Set up Renter B (different userId + different TenantProfile)
            UUID renterBUserId = UUID.randomUUID();
            String renterBClerkId = "clerk_renter_b_" + UUID.randomUUID();
            UUID renterBProfileId = UUID.randomUUID();

            User renterB = buildUser(renterBUserId, renterBClerkId, LANDLORD_TENANT_ID);
            TenantProfile renterBProfile = buildTenantProfileWithId(
                    renterBProfileId, LANDLORD_TENANT_ID, renterBClerkId);

            // Renter B has their OWN active lease
            Lease renterBLease = buildActiveLease(LANDLORD_TENANT_ID, renterBProfileId);

            when(userRepository.findById(renterBUserId)).thenReturn(Optional.of(renterB));
            when(tenantProfileRepository.findByClerkUserId(renterBClerkId))
                    .thenReturn(Optional.of(renterBProfile));
            when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                    .thenReturn(List.of(activeLease, renterBLease));

            // Renter A's entry (linked to Renter A's lease)
            UUID renterAEntryId = UUID.randomUUID();
            RentLedgerEntry renterAEntry = buildDueEntry(renterAEntryId, LANDLORD_TENANT_ID, activeLease.getId());

            when(rentLedgerEntryRepository.findByIdAndTenantId(renterAEntryId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(renterAEntry));

            // Renter B tries to pay Renter A's entry
            // The entry is found (same landlord tenant scope) but its leaseId
            // doesn't match Renter B's active lease
            assertThrows(RentLedgerStateException.class,
                    () -> service.initiateRentPayment(renterBUserId, renterAEntryId, "+254712345678"));

            verifyNoInteractions(rentPaymentInitiationService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Review submission (Phase 4b)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ReviewSubmission {

        @Test
        void submitReview_resolvesLandlordTenantAndActiveLease_callsCommandService() {
            LandlordReviewResponse response = new LandlordReviewResponse(
                    UUID.randomUUID(), "Test Renter", 5, "Great landlord", java.time.Instant.now());
            when(reviewQueryService.getRenterReview(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(response);

            LandlordReviewResponse result = service.submitReview(USER_ID, 5, "Great landlord");

            verify(reviewCommandService).submit(
                    eq(LANDLORD_TENANT_ID), eq(tenantProfile.getId()),
                    eq(activeLease.getId()), eq(5), eq("Great landlord"));
            assertSame(response, result);
        }

        @Test
        void submitReview_renterWithOnlyExpiredLease_canStillReview() {
            Lease expiredLease = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());
            expiredLease.expire();
            when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                    .thenReturn(List.of(expiredLease));
            when(reviewQueryService.getRenterReview(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(null);

            service.submitReview(USER_ID, 4, "Fair");

            verify(reviewCommandService).submit(
                    eq(LANDLORD_TENANT_ID), eq(tenantProfile.getId()),
                    eq(expiredLease.getId()), eq(4), eq("Fair"));
        }

        @Test
        void submitReview_renterWithNoVerifiableLease_throws() {
            when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                    .thenReturn(List.of());

            assertThrows(RentLedgerStateException.class,
                    () -> service.submitReview(USER_ID, 5, "nope"));
            verifyNoInteractions(reviewCommandService);
        }

        @Test
        void getMyReview_returnsNull_whenRenterHasNotReviewed() {
            when(reviewQueryService.getRenterReview(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(null);

            assertNull(service.getMyReview(USER_ID));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Maintenance submission (Phase 5) — context resolved server-side
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The renter portal NEVER sends unit/property/tenant-profile ids:
     * they are resolved from the authenticated renter's active lease.
     * Verify the command service receives those resolved ids.
     */
    @Test
    void submitMaintenanceRequest_resolvesContextFromActiveLease_delegatesWithResolvedIds() {
        UUID propertyId = UUID.randomUUID();
        Unit unit = mock(Unit.class);
        when(unit.getPropertyId()).thenReturn(propertyId);
        when(unitRepository.findByIdAndTenantId(activeLease.getUnitId(), LANDLORD_TENANT_ID))
                .thenReturn(Optional.of(unit));

        MaintenanceRequest created = MaintenanceRequest.submit(
                LANDLORD_TENANT_ID,
                activeLease.getUnitId(),
                propertyId,
                tenantProfile.getId(),
                activeLease.getId(),
                "Leaking taps",
                "Please repair the taps",
                MaintenanceCategory.PLUMBING,
                MaintenancePriority.HIGH,
                "renter@test.com",
                "corr-test");
        when(maintenanceRequestCommandService.submit(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);

        var response = service.submitMaintenanceRequest(
                USER_ID, "Leaking taps", "Please repair the taps",
                MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH);

        verify(maintenanceRequestCommandService).submit(
                eq(LANDLORD_TENANT_ID),
                eq(activeLease.getUnitId()),
                eq(propertyId),
                eq(tenantProfile.getId()),
                eq(activeLease.getId()),
                eq("Leaking taps"),
                eq("Please repair the taps"),
                eq(MaintenanceCategory.PLUMBING),
                eq(MaintenancePriority.HIGH),
                eq("renter@test.com"),
                any());
        assertEquals(activeLease.getUnitId(), response.unitId());
        assertEquals(propertyId, response.propertyId());
        assertEquals(tenantProfile.getId(), response.tenantProfileId());
    }

    @Test
    void submitMaintenanceRequest_renterWithNoActiveLease_throws() {
        when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                .thenReturn(List.of());

        assertThrows(RentLedgerStateException.class,
                () -> service.submitMaintenanceRequest(
                        USER_ID, "Leaking taps", null,
                        MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH));
        verifyNoInteractions(maintenanceRequestCommandService);
    }

    @Test
    void submitMaintenanceRequest_unitNotFound_throws() {
        when(unitRepository.findByIdAndTenantId(activeLease.getUnitId(), LANDLORD_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThrows(RentLedgerStateException.class,
                () -> service.submitMaintenanceRequest(
                        USER_ID, "Leaking taps", null,
                        MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH));
        verifyNoInteractions(maintenanceRequestCommandService);
    }

    /**
     * The renter's request list is scoped to their own profile — never
     * the whole tenant (other renters' requests are invisible).
     */
    @Test
    void getMaintenanceRequests_returnsOnlyRentersOwnRequests() {
        MaintenanceRequest mine = MaintenanceRequest.submit(
                LANDLORD_TENANT_ID, activeLease.getUnitId(), UUID.randomUUID(),
                tenantProfile.getId(), activeLease.getId(), "My issue", null,
                MaintenanceCategory.GENERAL, MaintenancePriority.MEDIUM,
                "renter@test.com", "corr-1");
        when(maintenanceRequestRepository.findByTenantIdAndTenantProfileId(
                LANDLORD_TENANT_ID, tenantProfile.getId()))
                .thenReturn(List.of(mine));

        var responses = service.getMaintenanceRequests(USER_ID);

        verify(maintenanceRequestRepository).findByTenantIdAndTenantProfileId(
                eq(LANDLORD_TENANT_ID), eq(tenantProfile.getId()));
        assertEquals(1, responses.size());
        assertEquals("My issue", responses.get(0).title());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private User buildUser(UUID userId, String clerkUserId, UUID tenantId) {
        return User.rehydrate(
                userId, 0L, clerkUserId, tenantId,
                "test@test.com", "Test", "User",
                true, UserRole.STAFF // renters don't have a separate UserRole; STAFF is the closest available
        );
    }

    private TenantProfile buildTenantProfile(UUID landlordTenantId, String clerkUserId) {
        return buildTenantProfileWithId(UUID.randomUUID(), landlordTenantId, clerkUserId);
    }

    private TenantProfile buildTenantProfileWithId(UUID profileId, UUID landlordTenantId, String clerkUserId) {
        TenantProfile profile = TenantProfile.create(
                landlordTenantId, clerkUserId,
                "Test Renter", "renter@test.com", "+254712345678",
                "12345678", "corr-" + UUID.randomUUID()
        );
        // Override the auto-generated ID with our test ID via rehydrate
        return TenantProfile.rehydrate(
                profileId, landlordTenantId, clerkUserId,
                "Test Renter", "renter@test.com", "+254712345678", "12345678"
        );
    }

    private Lease buildActiveLease(UUID tenantId, UUID tenantProfileId) {
        Lease lease = Lease.create(
                tenantId,
                UUID.randomUUID(), // propertyId
                UUID.randomUUID(), // unitId
                tenantProfileId,   // correct slot
                "LSE-PORTAL-TEST-" + System.nanoTime(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusMonths(9),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                BigDecimal.ZERO,
                0,
                false
        );
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        return lease;
    }

    private RentLedgerEntry buildDueEntry(UUID entryId, UUID tenantId, UUID leaseId) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getId()).thenReturn(entryId);
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getBalanceOwed()).thenReturn(new BigDecimal("20000.00"));
        return entry;
    }

    private RentPaymentRequest buildDummyPaymentRequest() {
        return mock(RentPaymentRequest.class);
    }
}
