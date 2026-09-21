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
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.application.autopay.AutoPayService;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse.PaymentHistoryItem;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.RenterReviewQueryService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.application.RenterIdentityLinker;
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
import java.time.LocalDateTime;
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
 *   - {@code TenantPortalController} now carries a class-level
 *     {@code @PreAuthorize("hasAuthority('ROLE_TENANT')")} (see
 *     TenantPortalControllerSecurityTest), but that only proves "some
 *     renter" is calling — it says nothing about WHICH renter. The
 *     controller passes the authenticated userId to the service, which
 *     resolves the caller's own TenantProfile via userId → User →
 *     clerkUserId → TenantProfile, and every subsequent lookup is scoped to
 *     that profile/tenant.
 *   - Cross-renter isolation is therefore still a SERVICE-layer concern, not
 *     something the controller's RBAC gate can express.
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
    private PropertyMediaRepository propertyMediaRepository;
    private TenantRepository tenantRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentTransactionRepository rentTransactionRepository;
    private RentPaymentInitiationService rentPaymentInitiationService;
    private RentPaymentRequestRepository rentPaymentRequestRepository;
    private AutoPayService autoPayService;
    private ReviewCommandService reviewCommandService;
    private ReviewQueryService reviewQueryService;
    private RenterReviewQueryService renterReviewQueryService;
    private MaintenanceRequestCommandService maintenanceRequestCommandService;
    private MaintenanceRequestRepository maintenanceRequestRepository;
    private StkPushRateLimiter stkPushRateLimiter;
    private DepositRepository depositRepository;

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
        propertyMediaRepository = mock(PropertyMediaRepository.class);
        tenantRepository = mock(TenantRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentTransactionRepository = mock(RentTransactionRepository.class);
        rentPaymentInitiationService = mock(RentPaymentInitiationService.class);
        rentPaymentRequestRepository = mock(RentPaymentRequestRepository.class);
        autoPayService = mock(AutoPayService.class);
        reviewCommandService = mock(ReviewCommandService.class);
        reviewQueryService = mock(ReviewQueryService.class);
        renterReviewQueryService = mock(RenterReviewQueryService.class);
        maintenanceRequestCommandService = mock(MaintenanceRequestCommandService.class);
        maintenanceRequestRepository = mock(MaintenanceRequestRepository.class);
        stkPushRateLimiter = mock(StkPushRateLimiter.class);
        depositRepository = mock(DepositRepository.class);

        service = new TenantPortalService(
                userRepository,
                tenantProfileRepository,
                mock(RenterIdentityLinker.class),
                leaseRepository,
                unitRepository,
                propertyRepository,
                propertyMediaRepository,
                tenantRepository,
                rentLedgerEntryRepository,
                rentTransactionRepository,
                rentPaymentInitiationService,
                rentPaymentRequestRepository,
                autoPayService,
                reviewCommandService,
                reviewQueryService,
                renterReviewQueryService,
                maintenanceRequestCommandService,
                maintenanceRequestRepository,
                mock(AnnouncementQueryService.class),
                stkPushRateLimiter,
                depositRepository
        );

        renterUser = buildUser(USER_ID, CLERK_USER_ID, LANDLORD_TENANT_ID);
        tenantProfile = buildTenantProfile(LANDLORD_TENANT_ID, CLERK_USER_ID);
        activeLease = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(renterUser));
        when(tenantProfileRepository.findAllByClerkUserId(CLERK_USER_ID))
                .thenReturn(List.of(tenantProfile));
        when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                .thenReturn(List.of(activeLease));
            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
            when(tenantProfileRepository.findAllByClerkUserId(clerkId))
                    .thenReturn(List.of());

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
                when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
    // STK push rate limiting -- both initiate entry points must consult the
    // limiter FIRST, before any repository/Daraja work, and must propagate
    // its rejection rather than swallow it.
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class StkPushRateLimiting {

        @Test
        void initiateRentPayment_checksRateLimiterBeforeProceeding() {
            UUID entryId = UUID.randomUUID();
            RentLedgerEntry entry = buildDueEntry(entryId, LANDLORD_TENANT_ID, activeLease.getId());
            when(rentLedgerEntryRepository.findByIdAndTenantId(entryId, LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(entry));
            when(rentPaymentInitiationService.initiate(any(), any(), any()))
                    .thenReturn(buildDummyPaymentRequest());

            service.initiateRentPayment(USER_ID, entryId, "+254712345678");

            verify(stkPushRateLimiter).checkAndRecord(USER_ID);
        }

        @Test
        void initiateRentPayment_rateLimiterRejection_propagatesAndSkipsPaymentInitiation() {
            UUID entryId = UUID.randomUUID();
            doThrow(new com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException(
                    "slow down", 15L))
                    .when(stkPushRateLimiter).checkAndRecord(USER_ID);

            assertThrows(
                    com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException.class,
                    () -> service.initiateRentPayment(USER_ID, entryId, "+254712345678"));

            verifyNoInteractions(userRepository);
            verifyNoInteractions(rentLedgerEntryRepository);
            verifyNoInteractions(rentPaymentInitiationService);
        }

        @Test
        void initiatePortalPayment_rateLimiterRejection_propagatesAndSkipsPaymentInitiation() {
            doThrow(new com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException(
                    "slow down", 15L))
                    .when(stkPushRateLimiter).checkAndRecord(USER_ID);

            assertThrows(
                    com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException.class,
                    () -> service.initiatePortalPayment(USER_ID, new BigDecimal("1000.00"), "+254712345678"));

            verifyNoInteractions(userRepository);
            verifyNoInteractions(leaseRepository);
            verifyNoInteractions(rentPaymentInitiationService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Deposit visibility -- renter-facing, read-only. getDeposit() must
    // never expose a method to refund/forfeit; only DepositController
    // (OWNER/MANAGER-gated) can mutate a deposit.
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class DepositVisibility {

        @Test
        void depositExists_returnsItScoped_toRenterActiveLease() {
            Deposit deposit = Deposit.rehydrate(
                    UUID.randomUUID(), LANDLORD_TENANT_ID, activeLease.getId(), UUID.randomUUID(),
                    tenantProfile.getId(), new BigDecimal("15000.00"), new BigDecimal("15000.00"),
                    BigDecimal.ZERO, DepositStatus.HELD, java.time.LocalDateTime.now(), null, "KES",
                    null, null, null, null,
                    null, null, null, null, null, null
            );
            when(depositRepository.findByLeaseIdAndTenantId(activeLease.getId(), LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(deposit));

            var response = service.getDeposit(USER_ID);

            assertEquals("HELD", response.status());
            assertEquals(new BigDecimal("15000.00"), response.amountPaid());
        }

        @Test
        void noDepositForLease_returnsNull_notAnException() {
            when(depositRepository.findByLeaseIdAndTenantId(activeLease.getId(), LANDLORD_TENANT_ID))
                    .thenReturn(Optional.empty());

            assertNull(service.getDeposit(USER_ID));
        }

        /**
         * Regression guard for the deployment blocker (2026-09-03). Every
         * renter endpoint used to require an ACTIVE lease, and
         * LeaseActionScheduler expires leases automatically at 01:30 — so on
         * the night a tenancy ended, the renter lost their deposit record.
         * That is precisely when they need it: the deposit is refunded after
         * move-out, and this page is where they watch that happen. The lease
         * page even promises it in copy.
         */
        @Test
        void depositStaysVisibleAfterTheLeaseHasExpired() {
            Lease expired = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());
            expired.expire();
            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(List.of(expired));

            Deposit deposit = Deposit.rehydrate(
                    UUID.randomUUID(), LANDLORD_TENANT_ID, expired.getId(), UUID.randomUUID(),
                    tenantProfile.getId(), new BigDecimal("15000.00"), new BigDecimal("15000.00"),
                    new BigDecimal("15000.00"), DepositStatus.REFUNDED, java.time.LocalDateTime.now(),
                    java.time.LocalDateTime.now(), "KES",
                    null, null, null, null,
                    null, null, null, null, null, null
            );
            when(depositRepository.findByLeaseIdAndTenantId(expired.getId(), LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(deposit));

            var response = service.getDeposit(USER_ID);

            assertEquals("REFUNDED", response.status());
        }
    }

    private Unit mockUnitForDashboard() {
        Unit unit = mock(Unit.class);
        when(unit.getPropertyId()).thenReturn(UUID.randomUUID());
        when(unit.getUnitNumber()).thenReturn("HWSW");
        when(unit.getLabel()).thenReturn("Bright");
        return unit;
    }

    private com.rentmanager.modules.property.domain.model.Property mockPropertyForDashboard() {
        var property = mock(com.rentmanager.modules.property.domain.model.Property.class);
        when(property.getName()).thenReturn("Green Land Apartments");
        return property;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Next due date: projected from the charge scheduler's own rule when no
    // future-dated entry has been posted yet.
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class NextDueProjection {

        /**
         * Every domain mock is built BEFORE any when(...) chain opens —
         * constructing a mock inside an open stubbing throws
         * UnfinishedStubbingException (see this repo's test conventions).
         */
        private void dashboardFixtures(Lease lease, List<RentLedgerEntry> entries) {
            Unit unit = mockUnitForDashboard();
            var property = mockPropertyForDashboard();

            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(List.of(lease));
            when(unitRepository.findByIdAndTenantId(lease.getUnitId(), LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(unit));
            when(propertyRepository.findByIdAndTenantId(any(), eq(LANDLORD_TENANT_ID)))
                    .thenReturn(Optional.of(property));
            when(rentLedgerEntryRepository.findByLease(LANDLORD_TENANT_ID, lease.getId()))
                    .thenReturn(entries);
            when(rentTransactionRepository.findByLease(LANDLORD_TENANT_ID, lease.getId()))
                    .thenReturn(List.of());
        }

        private RentLedgerEntry postedEntry(LocalDate periodStart, RentLedgerStatus status) {
            RentLedgerEntry entry = mock(RentLedgerEntry.class);
            when(entry.getBillingPeriodStart()).thenReturn(periodStart);
            when(entry.getDueDate()).thenReturn(periodStart);
            when(entry.getStatus()).thenReturn(status);
            when(entry.getBalanceOwed()).thenReturn(new BigDecimal("1.00"));
            when(entry.getAmountDue()).thenReturn(new BigDecimal("1.00"));
            return entry;
        }

        /**
         * The reported symptom: every charge posted so far is dated in the
         * past (the scheduler never posts ahead), so the "next due" filter
         * matched nothing and the dashboard read "Not scheduled" — for every
         * renter, for most of every month.
         */
        @Test
        void projectsTheNextCalendarMonthWhenEveryPostedChargeIsInThePast() {
            List<RentLedgerEntry> entries = List.of(
                    postedEntry(LocalDate.of(2026, 8, 1), RentLedgerStatus.OVERDUE),
                    postedEntry(LocalDate.of(2026, 9, 1), RentLedgerStatus.OVERDUE));
            dashboardFixtures(activeLease, entries);

            var dashboard = service.getDashboard(USER_ID);

            // September was the last period posted, so October is next, due
            // on the 1st — exactly what RentChargeScheduler will post.
            assertEquals(LocalDate.of(2026, 10, 1), dashboard.nextDueDate());
            assertEquals(0, dashboard.nextDueAmount().compareTo(activeLease.getRentAmount()));
        }

        @Test
        void aRealFutureDatedEntryStillWinsOverTheProjection() {
            LocalDate future = LocalDate.now().plusMonths(1).withDayOfMonth(1);
            RentLedgerEntry upcoming = postedEntry(future, RentLedgerStatus.DUE);
            dashboardFixtures(activeLease, List.of(upcoming));

            var dashboard = service.getDashboard(USER_ID);

            assertEquals(future, dashboard.nextDueDate());
            // Amount comes from the real entry, not the lease's rent figure.
            assertEquals(0, dashboard.nextDueAmount().compareTo(new BigDecimal("1.00")));
        }

        @Test
        void projectsFromTheLeaseStartWhenNothingHasBeenPostedYet() {
            dashboardFixtures(activeLease, List.of());

            var dashboard = service.getDashboard(USER_ID);

            assertEquals(
                    java.time.YearMonth.from(activeLease.getStartDate()).plusMonths(1).atDay(1),
                    dashboard.nextDueDate());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Lease lifecycle: read access survives the end of a tenancy, but
    // anything that moves money does not.
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class EndedTenancyAccess {

        private Lease expiredOnly() {
            Lease expired = buildActiveLease(LANDLORD_TENANT_ID, tenantProfile.getId());
            expired.expire();
            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(List.of(expired));
            return expired;
        }

        @Test
        void paymentHistoryStaysReadableAfterTheLeaseEnds() {
            Lease expired = expiredOnly();
            when(rentTransactionRepository.findByLease(LANDLORD_TENANT_ID, expired.getId()))
                    .thenReturn(List.of());

            TenantPaymentHistoryResponse response = service.getPaymentHistory(USER_ID, 0, 20);

            assertNotNull(response);
            assertEquals(0, response.totalElements());
        }

        @Test
        void theLeaseItselfStaysReadableAfterItEnds() {
            expiredOnly();

            // Reaching the mapping at all is the assertion: before the fix
            // this threw RentLedgerStateException before touching any of it.
            assertThrows(RentLedgerStateException.class, () -> service.getLease(USER_ID),
                    "unit/property lookups are unstubbed here, so it must fail LATER than the lease lookup");
        }

        /**
         * The other half of the fix: read access widened, but collecting
         * rent against a tenancy that has ended must still fail closed.
         */
        @Test
        void payingRentIsStillRefusedOnceTheLeaseHasEnded() {
            expiredOnly();

            assertThrows(RentLedgerStateException.class,
                    () -> service.initiatePortalPayment(USER_ID, new BigDecimal("1000"), "+254712345678"));
        }

        /**
         * The dashboard must not promise a renter another rent charge once
         * their tenancy is over.
         */
        @Test
        void noNextDueDateIsProjectedForAnEndedTenancy() {
            Lease expired = expiredOnly();
            Unit unit = mockUnitForDashboard();
            var property = mockPropertyForDashboard();

            when(unitRepository.findByIdAndTenantId(expired.getUnitId(), LANDLORD_TENANT_ID))
                    .thenReturn(Optional.of(unit));
            when(propertyRepository.findByIdAndTenantId(any(), eq(LANDLORD_TENANT_ID)))
                    .thenReturn(Optional.of(property));
            when(rentLedgerEntryRepository.findByLease(LANDLORD_TENANT_ID, expired.getId()))
                    .thenReturn(List.of());
            when(rentTransactionRepository.findByLease(LANDLORD_TENANT_ID, expired.getId()))
                    .thenReturn(List.of());

            var dashboard = service.getDashboard(USER_ID);

            assertNull(dashboard.nextDueDate());
            assertEquals(0, dashboard.nextDueAmount().compareTo(BigDecimal.ZERO));
        }

        @Test
        void aRenterWithNoLeaseAtAllStillGetsAClearFailure() {
            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
                    .thenReturn(List.of());

            assertThrows(RentLedgerStateException.class, () -> service.getPaymentHistory(USER_ID, 0, 20));
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
            when(tenantProfileRepository.findAllByClerkUserId(renterBClerkId))
                .thenReturn(List.of(renterBProfile));
            when(leaseRepository.findAllByTenant(LANDLORD_TENANT_ID))
                    .thenReturn(List.of(activeLease, renterBLease));
                when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
                    UUID.randomUUID(), "Test Renter", 5, "Great landlord", com.rentmanager.modules.review.domain.enums.ReviewStatus.APPROVED, java.time.Instant.now());
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
                when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
                when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
    // Payment history: status and billing period come from the ledger
    // entry, not the transaction — regression guard for a real defect a
    // renter reported (2026-09-03): toPaymentHistoryItem() used to set
    // `status` to txn.getType().name(), so every row's Status column showed
    // "Rent Charge" / "Payment" instead of PAID/OVERDUE/DUE, and
    // billingPeriodStart/End were hardcoded to "", so the Period column
    // always read "— – —".
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class PaymentHistoryStatusAndBillingPeriod {

        @Test
        void statusComesFromTheLinkedLedgerEntry_notTheTransactionType() {
            UUID entryId = UUID.randomUUID();
            RentLedgerEntry entry = mock(RentLedgerEntry.class);
            when(entry.getId()).thenReturn(entryId);
            when(entry.getStatus()).thenReturn(RentLedgerStatus.OVERDUE);
            when(entry.getBillingPeriodStart()).thenReturn(LocalDate.of(2026, 9, 1));
            when(entry.getBillingPeriodEnd()).thenReturn(LocalDate.of(2026, 9, 30));

            RentTransaction charge = RentTransaction.create(
                    LANDLORD_TENANT_ID, entryId, activeLease.getId(), RentTransactionType.RENT_CHARGE,
                    new BigDecimal("20000.00"), null, RentTransactionSource.SYSTEM,
                    "SYSTEM", LocalDateTime.now()
            );

            when(rentTransactionRepository.findByLease(LANDLORD_TENANT_ID, activeLease.getId()))
                    .thenReturn(List.of(charge));
            when(rentLedgerEntryRepository.findAllByTenantAndIdIn(LANDLORD_TENANT_ID, List.of(entryId)))
                    .thenReturn(List.of(entry));

            TenantPaymentHistoryResponse response = service.getPaymentHistory(USER_ID, 0, 20);

            assertEquals(1, response.content().size());
            PaymentHistoryItem item = response.content().get(0);
            // The bug: this used to equal "RENT_CHARGE" (txn.getType().name()).
            assertEquals("OVERDUE", item.status());
            assertEquals("2026-09-01", item.billingPeriodStart());
            assertEquals("2026-09-30", item.billingPeriodEnd());
        }

        @Test
        void aTransactionWhoseLedgerEntryCannotBeFound_fallsBackRatherThanThrowing() {
            // Defensive path: findAllByTenantAndIdIn() returns no match for
            // this transaction's ledgerEntryId (should not happen given the
            // domain's non-null constraint, but must degrade safely rather
            // than NPE on a renter's history page if it ever did).
            UUID entryId = UUID.randomUUID();
            RentTransaction payment = RentTransaction.create(
                    LANDLORD_TENANT_ID, entryId, activeLease.getId(), RentTransactionType.PAYMENT,
                    new BigDecimal("20000.00"), "MPESA-1", RentTransactionSource.MPESA,
                    "system", LocalDateTime.now()
            );

            when(rentTransactionRepository.findByLease(LANDLORD_TENANT_ID, activeLease.getId()))
                    .thenReturn(List.of(payment));
            when(rentLedgerEntryRepository.findAllByTenantAndIdIn(LANDLORD_TENANT_ID, List.of(entryId)))
                    .thenReturn(List.of());

            TenantPaymentHistoryResponse response = service.getPaymentHistory(USER_ID, 0, 20);

            PaymentHistoryItem item = response.content().get(0);
            assertEquals("PAYMENT", item.status());
            assertEquals("", item.billingPeriodStart());
            assertEquals("", item.billingPeriodEnd());
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
            when(leaseRepository.findAllByTenantAndTenantProfile(LANDLORD_TENANT_ID, tenantProfile.getId()))
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
