package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.maintenance.api.dto.MaintenanceRequestResponse;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.model.PropertyMedia;
import com.rentmanager.modules.property.domain.repository.PropertyMediaRepository;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.rentledger.api.autopay.dto.AutoPaySettingsResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentPaymentRequestResponse;
import com.rentmanager.modules.rentledger.application.autopay.AutoPayService;
import com.rentmanager.modules.rentledger.domain.model.autopay.AutoPaySettings;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse.PaymentHistoryItem;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentReceiptResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentSummaryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.WhatsAppOptInResponse;
import com.rentmanager.modules.announcement.api.dto.RenterAnnouncementResponse;
import com.rentmanager.modules.announcement.application.AnnouncementQueryService;
import com.rentmanager.modules.deposit.api.dto.response.DepositResponse;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.review.application.RenterReviewQueryService;
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.RenterReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.application.RenterIdentityLinker;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantPortalService {

    /**
     * Lease statuses that prove a tenancy was actually active at some
     * point - the review eligibility bar (kept in sync with the command
     * service's verification rule).
     */
    private static final EnumSet<LeaseStatus> VERIFIED_LEASE_STATUSES =
            EnumSet.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED,
                    LeaseStatus.EXPIRED, LeaseStatus.TERMINATED, LeaseStatus.SUSPENDED);

    /**
     * A tenancy that is live right now: occupied and being billed. RENEWED is
     * exactly as live as ACTIVE — RentChargeScheduler posts rent for both —
     * so every "current lease" check here must accept both. Treating only
     * ACTIVE as current meant a renter who renewed was still charged rent but
     * could no longer pay it through the portal.
     */
    private static final EnumSet<LeaseStatus> CURRENT_LEASE_STATUSES =
            EnumSet.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED);

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final RenterIdentityLinker renterIdentityLinker;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final TenantRepository tenantRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;
    private final RentPaymentInitiationService rentPaymentInitiationService;
    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final AutoPayService autoPayService;
    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;
    private final RenterReviewQueryService renterReviewQueryService;
    private final MaintenanceRequestCommandService maintenanceRequestCommandService;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final AnnouncementQueryService announcementQueryService;
    private final StkPushRateLimiter stkPushRateLimiter;
    private final DepositRepository depositRepository;

    @Transactional(readOnly = true)
    public TenantDashboardResponse getDashboard(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease lease = findLeaseForReadAccess(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));
        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, lease.getId());

        BigDecimal currentBalance = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = entries.stream()
                .filter(e -> e.getStatus() == RentLedgerStatus.OVERDUE)
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        RentLedgerEntry nextDueEntry = entries.stream()
                .filter(e -> e.getDueDate() != null && !e.getDueDate().isBefore(LocalDate.now()))
                .filter(e -> e.getStatus() != RentLedgerStatus.PAID && e.getStatus() != RentLedgerStatus.OVERPAID)
                .min(Comparator.comparing(RentLedgerEntry::getDueDate))
                .orElse(null);

        // FIX (2026-09-04): the filter above only accepts an entry dated today
        // or later, but RentChargeScheduler never posts ahead — it posts each
        // calendar month up to the current one and stops. So for most of every
        // month no future-dated entry exists, and the portal told every renter
        // their next rent was "Not scheduled". Rent is the most predictable
        // obligation a renter has; the one date they most need is the one the
        // dashboard was refusing to show.
        //
        // When nothing is posted yet for the coming period, project it instead
        // — from the same rule the scheduler itself applies, so the date shown
        // is the date the renter will actually be charged on.
        LocalDate projectedDueDate = nextDueEntry == null ? projectNextDueDate(lease, entries) : null;

        // First unpaid entry (earliest due date with balance > 0) — used by
        // the Pay Now button to know which entry to charge.
        RentLedgerEntry firstUnpaidEntry = entries.stream()
                .filter(e -> e.getBalanceOwed().compareTo(BigDecimal.ZERO) > 0)
                .filter(e -> e.getStatus() != RentLedgerStatus.PAID && e.getStatus() != RentLedgerStatus.OVERPAID)
                .min(Comparator.comparing(RentLedgerEntry::getDueDate))
                .orElse(null);

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, lease.getId());
        List<RentTransaction> recentPayments = allTxns.stream()
                // reducesBalanceOwed() no longer includes DEPOSIT (it's audit-only,
                // held separately by the deposit module) — added back explicitly so
                // the tenant still sees their deposit receipt in recent activity.
                .filter(t -> t.reducesBalanceOwed() || t.getType() == RentTransactionType.DEPOSIT)
                .sorted(Comparator.comparing(RentTransaction::getOccurredAt).reversed())
                .limit(5)
                .toList();
        Map<UUID, RentLedgerEntry> recentPaymentsEntries = loadLedgerEntriesFor(landlordTenantId, recentPayments);

        return new TenantDashboardResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                currentBalance,
                firstUnpaidEntry != null ? firstUnpaidEntry.getId() : null,
                nextDueEntry != null ? nextDueEntry.getDueDate() : projectedDueDate,
                // A projected period has no ledger entry yet, so its amount is
                // the lease's contractual rent. Still ZERO when there is no
                // next charge at all, so the portal shows nothing rather than
                // quoting a figure for a charge that will never be posted.
                nextDueEntry != null ? nextDueEntry.getAmountDue()
                        : projectedDueDate != null ? lease.getRentAmount() : BigDecimal.ZERO,
                overdueAmount,
                lease.getStatus().name(),
                unit.getUnitNumber(),
                property.getName(),
                lease.getRentAmount(),
                lease.getSecurityDeposit() != null ? lease.getSecurityDeposit() : BigDecimal.ZERO,
                recentPayments.stream().map(t -> toPaymentHistoryItem(t, recentPaymentsEntries)).toList()
        );
    }

    @Transactional(readOnly = true)
    public TenantLeaseResponse getLease(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease lease = findLeaseForReadAccess(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));
        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));
        Tenant landlord = tenantRepository.findById(landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        String address = "";
        if (property.getAddress() != null) {
            address = property.getAddress().toString();
        }

        String landlordLogoUrl = null;
        if (landlord.getBrandingSettings() != null) {
            landlordLogoUrl = landlord.getBrandingSettings().getLogoUrl();
        }

        // Verification means the landlord finished onboarding and was
        // approved — which is precisely what Tenant.status models: it starts
        // at PENDING and reaches ACTIVE only through a deliberate activate().
        //
        // This used to read subscription status and count TRIAL as verified,
        // so a five-minute-old free trial displayed a verification badge to
        // renters. Paying for something is not the same as having been
        // checked, and a badge a renter cannot rely on is worse than no badge
        // because it lends the platform's credibility to accounts nobody
        // vetted. Subscription state still drives premium branding, which is
        // what it is actually evidence of.
        boolean landlordVerified = landlord.getStatus() == TenantStatus.ACTIVE;

        // Phase 2a (free tier): the org-level manager (UserRole.MANAGER) is
        // the renter's real-world point of contact. Exposed only when one
        // actually exists - never a placeholder.
        User manager = userRepository.findByTenantIdAndRole(landlordTenantId, UserRole.MANAGER)
                .stream()
                .filter(u -> u.isActive())
                .findFirst()
                .orElse(null);

        String managerName = null;
        String managerPhone = null;
        String managerEmail = null;
        if (manager != null) {
            managerName = (manager.getFirstName() + " " + manager.getLastName()).trim();
            managerPhone = null; // User has no phone field - contact via email
            managerEmail = manager.getEmail();
        }

        // Phase 2b (free tier): landlord-set emergency contact, rendered on
        // the portal only when actually configured.
        String emergencyContactPhone = landlord.getEmergencyContactPhone();
        boolean emergencyContact24h = landlord.isEmergencyContact24h();

        // Phase 3a/3b (PREMIUM gate): branded theme colors and the premium
        // badge are only delivered to a premium, paying landlord. Colors
        // stay null otherwise and the portal falls back to defaults. The
        // existing landlordLogoUrl behavior is deliberately unchanged.
        boolean premiumActive = landlord.getBillingMode() == BillingMode.PREMIUM_MONTHLY
                && landlord.getSubscriptionStatus() != null
                && (landlord.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                    || landlord.getSubscriptionStatus() == SubscriptionStatus.TRIAL
                    || landlord.getSubscriptionStatus() == SubscriptionStatus.GRACE_PERIOD);

        String primaryColor = null;
        String secondaryColor = null;
        if (premiumActive && landlord.getBrandingSettings() != null) {
            primaryColor = landlord.getBrandingSettings().getPrimaryColor();
            secondaryColor = landlord.getBrandingSettings().getSecondaryColor();
        }

        String propertyThumbnailUrl = propertyMediaRepository
                .findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
                        property.getTenantId(),
                        property.getId()
                )
                .map(PropertyMedia::getFileUrl)
                .orElse(null);

        return new TenantLeaseResponse(
                lease.getId(),
                lease.getLeaseNumber(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                lease.getSecurityDeposit() != null ? lease.getSecurityDeposit() : BigDecimal.ZERO,
                lease.getStatus().name(),
                unit.getUnitNumber(),
                unit.getLabel(),
                property.getName(),
                address,
                landlord.getName(),
                landlord.getPhoneNumber(),
                landlord.getEmail(),
                landlord.getTenantCode(),
                landlord.getAddress(),
                landlordLogoUrl,
                // FIX (2026-09-03): this used to be landlord.getCreatedAt() —
                // when the LANDLORD signed up for RentManager, not when this
                // renter's tenancy with them began. The portal renders it as
                // "Renting with this landlord since {date}", a claim about
                // the renter's own history; a landlord who joined the
                // platform in September but has rented to this tenant since
                // July would show the wrong month for a fact the renter can
                // check against their own memory. The active lease's start
                // date is what "since" actually means here.
                lease.getStartDate() != null ? lease.getStartDate().toString() : null,
                landlordVerified,
                "",
                managerName,
                managerPhone,
                managerEmail,
                emergencyContactPhone,
                emergencyContact24h,
                primaryColor,
                secondaryColor,
                landlord.getBillingMode() != null ? landlord.getBillingMode().name() : null,
                landlord.getSubscriptionStatus() != null ? landlord.getSubscriptionStatus().name() : null,
                propertyThumbnailUrl
        );
    }

    /**
     * Renter-facing, READ-ONLY deposit visibility (trust feature: a renter
     * can currently see depositAmount as a bare figure in getDashboard()/
     * getLease() but has no way to know whether it's held, refunded, or
     * forfeited). Deliberately reuses DepositResponse as-is rather than a
     * separate DTO - every field on it (amounts, currency, status,
     * paidAt/refundedAt) is already safe for a renter to see; there is no
     * landlord-only field to strip.
     *
     * Returns null (not a 404) when no deposit exists yet for this lease -
     * same "may legitimately not exist" convention as getMyReview() above -
     * the controller maps that to a 200 with an explanatory message rather
     * than an error.
     *
     * Renters can only ever READ their own deposit through this path -
     * refund()/forfeit() remain exclusively on DepositController, gated to
     * OWNER/MANAGER. Never add a mutating deposit method to this service.
     */
    @Transactional(readOnly = true)
    public DepositResponse getDeposit(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        Lease lease = findLeaseForReadAccess(profile.getTenantId(), profile.getId());
        return depositRepository.findByLeaseIdAndTenantId(lease.getId(), profile.getTenantId())
                .map(DepositResponse::from)
                .orElse(null);
    }

    /**
     * Phase 4b: submits a landlord review on behalf of the authenticated
     * renter. The landlord tenant id is the renter profile's tenant
     * (never client-supplied), and the lease is resolved from the renter's
     * own tenancy history - the command service then re-verifies both
     * against the landlord's leases before persisting.
     */
    @Transactional
    public LandlordReviewResponse submitReview(UUID userId, int rating, String comment) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        UUID leaseId = findVerifiableLeaseId(landlordTenantId, profile.getId());

        reviewCommandService.submit(landlordTenantId, profile.getId(), leaseId, rating, comment);

        return reviewQueryService.getRenterReview(landlordTenantId, profile.getId());
    }

    @Transactional(readOnly = true)
    public LandlordReviewResponse getMyReview(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return reviewQueryService.getRenterReview(profile.getTenantId(), profile.getId());
    }

    /**
     * V65: the landlord reviews written about this renter — approved only,
     * scoped to the authenticated renter's own profile.
     */
    @Transactional(readOnly = true)
    public List<RenterReviewResponse> getReviewsReceived(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return renterReviewQueryService.getApprovedReviews(profile.getTenantId(), profile.getId());
    }

    /**
     * V65: summary of the approved reviews written about this renter —
     * same honesty rule as the landlord side (average hidden below 3
     * reviews).
     */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewsReceivedSummary(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return renterReviewQueryService.getSummaryForProfile(profile.getTenantId(), profile.getId());
    }

    /**
     * Any lease that proves a real tenancy (active, renewed, expired,
     * terminated or suspended) qualifies a renter to review - preferring
     * the currently active lease when one exists.
     */
    private UUID findVerifiableLeaseId(UUID landlordTenantId, UUID tenantProfileId) {
        List<Lease> leases = leaseRepository.findAllByTenant(landlordTenantId).stream()
                .filter(l -> l.getTenantProfileId() != null
                        && l.getTenantProfileId().equals(tenantProfileId))
                .toList();

        return leases.stream()
                .filter(l -> CURRENT_LEASE_STATUSES.contains(l.getStatus()))
                .findFirst()
                .map(Lease::getId)
                .or(() -> leases.stream()
                        .filter(l -> VERIFIED_LEASE_STATUSES.contains(l.getStatus()))
                        .findFirst()
                        .map(Lease::getId))
                .orElseThrow(() -> new RentLedgerStateException(
                        "Only verified renters with an active or past lease can review a landlord",
                        ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public TenantPaymentSummaryResponse getPaymentSummary(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease lease = findLeaseForReadAccess(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, lease.getId());

        BigDecimal totalPaid = allTxns.stream()
                .filter(t -> t.reducesBalanceOwed())
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDue = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.RENT_CHARGE)
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, lease.getId());
        BigDecimal currentBalance = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = entries.stream()
                .filter(e -> e.getStatus() == RentLedgerStatus.OVERDUE)
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int currentYear = LocalDate.now().getYear();
        long paymentsThisYear = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.PAYMENT)
                .filter(t -> t.getOccurredAt() != null && t.getOccurredAt().getYear() == currentYear)
                .count();

        RentTransaction lastPayment = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.PAYMENT)
                .max(Comparator.comparing(RentTransaction::getOccurredAt))
                .orElse(null);

        return new TenantPaymentSummaryResponse(
                totalPaid,
                totalDue,
                currentBalance,
                overdueAmount,
                (int) paymentsThisYear,
                lastPayment != null ? lastPayment.getOccurredAt() : null,
                lastPayment != null ? lastPayment.getAmount() : BigDecimal.ZERO
        );
    }

    @Transactional(readOnly = true)
    public TenantPaymentHistoryResponse getPaymentHistory(UUID userId, int page, int size) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease lease = findLeaseForReadAccess(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, lease.getId());
        List<RentTransaction> sorted = allTxns.stream()
                .sorted(Comparator.comparing(RentTransaction::getOccurredAt).reversed())
                .toList();

        int totalElements = sorted.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<PaymentHistoryItem> pageContent;
        if (fromIndex >= totalElements) {
            pageContent = List.of();
        } else {
            List<RentTransaction> pageTxns = sorted.subList(fromIndex, toIndex);
            Map<UUID, RentLedgerEntry> pageEntries = loadLedgerEntriesFor(landlordTenantId, pageTxns);
            pageContent = pageTxns.stream()
                    .map(t -> toPaymentHistoryItem(t, pageEntries))
                    .toList();
        }

        return new TenantPaymentHistoryResponse(
                pageContent,
                totalElements,
                totalPages,
                page,
                size,
                page == 0,
                page >= totalPages - 1,
                pageContent.isEmpty()
        );
    }

    @Transactional(readOnly = true)
    public TenantPaymentReceiptResponse getPaymentReceipt(UUID userId, UUID transactionId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();

        RentTransaction txn = rentTransactionRepository.findByIdAndTenantId(transactionId, landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Transaction not found", ErrorCode.RESOURCE_NOT_FOUND));

        Lease lease = leaseRepository.findByIdAndTenantId(txn.getLeaseId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Lease not found", ErrorCode.RESOURCE_NOT_FOUND));

        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));

        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, lease.getId());
        BigDecimal balanceAfter = entries.stream()
                .map(RentLedgerEntry::getBalanceOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String receiptNumber = "RCP-" + txn.getId().toString().substring(0, 8).toUpperCase();
        String billingPeriod = "";
        if (!entries.isEmpty()) {
            billingPeriod = entries.get(0).getBillingPeriodStart().toString();
        }

        return new TenantPaymentReceiptResponse(
                txn.getId(),
                receiptNumber,
                txn.getOccurredAt(),
                txn.getAmount(),
                txn.getExternalReference(),
                profile.getFullName(),
                profile.getPhone(),
                unit.getUnitNumber(),
                property.getName(),
                billingPeriod,
                billingPeriod,
                balanceAfter,
                null,
                null
        );
    }

    /**
     * The renter profile the portal acts on.
     *
     * <p>One person can hold several profiles — one per landlord they have
     * rented from. The portal serves their current home: the profile with a
     * live lease (ACTIVE/RENEWED), else one about to start, else the most
     * recent tenancy. The previous single-result lookup threw as soon as a
     * renter moved between two RentManager landlords, taking the whole
     * portal (balance, payments, repairs) down for exactly the people who had
     * just moved in somewhere new.
     */
    private TenantProfile resolveTenantProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RentLedgerStateException("User not found", ErrorCode.RESOURCE_NOT_FOUND));
        List<TenantProfile> profiles = tenantProfileRepository.findAllByClerkUserId(user.getClerkUserId());
        if (profiles.isEmpty()) {
            // A renter whose landlord entered their tenancy before they had an
            // account: the record exists but holds no identity yet. Claim it
            // here, on their verified email, the first time they open the
            // portal — otherwise they would be told "no tenancy found" beside
            // a tenancy their landlord is already managing.
            profiles = renterIdentityLinker.linkByVerifiedEmail(user.getClerkUserId(), user.getEmail());
        }
        if (profiles.isEmpty()) {
            throw new RentLedgerStateException("Tenant profile not found", ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (profiles.size() == 1) {
            return profiles.get(0);
        }
        return profiles.stream()
                .max(java.util.Comparator
                        .comparingInt((TenantProfile p) -> tenancyRank(p))
                        .thenComparing(this::latestLeaseStart, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElseThrow();
    }

    private int tenancyRank(TenantProfile profile) {
        List<Lease> leases = leaseRepository.findAllByTenantAndTenantProfile(profile.getTenantId(), profile.getId());
        if (leases.stream().anyMatch(l -> CURRENT_LEASE_STATUSES.contains(l.getStatus()))) {
            return 3;
        }
        if (leases.stream().anyMatch(l -> l.getStatus() == LeaseStatus.PENDING_ACTIVATION
                || l.getStatus() == LeaseStatus.AWAITING_DEPOSIT)) {
            return 2;
        }
        return leases.isEmpty() ? 0 : 1;
    }

    private LocalDate latestLeaseStart(TenantProfile profile) {
        return leaseRepository.findAllByTenantAndTenantProfile(profile.getTenantId(), profile.getId()).stream()
                .map(Lease::getStartDate)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }

    // -------------------------------------------------------
    // MAINTENANCE (Phase 5) — renter-scoped
    //
    // Unit/property/tenant-profile ids are NEVER taken from the
    // client: they are resolved from the authenticated renter's
    // active lease, so a renter can only ever raise requests for
    // their own unit (and the notification listener can fan out
    // to the right landlord).
    // -------------------------------------------------------

    @Transactional
    public MaintenanceRequestResponse submitMaintenanceRequest(
            UUID userId,
            String title,
            String description,
            MaintenanceCategory category,
            MaintenancePriority priority
    ) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease lease = findLeaseForReadAccess(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(lease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));

        MaintenanceRequest request = maintenanceRequestCommandService.submit(
                landlordTenantId,
                lease.getUnitId(),
                unit.getPropertyId(),
                profile.getId(),
                lease.getId(),
                title,
                description,
                category,
                priority,
                profile.getEmail() != null ? profile.getEmail() : profile.getFullName(),
                UUID.randomUUID().toString()
        );

        return MaintenanceRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequestResponse> getMaintenanceRequests(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return maintenanceRequestRepository
                .findByTenantIdAndTenantProfileId(profile.getTenantId(), profile.getId())
                .stream()
                .map(MaintenanceRequestResponse::from)
                .toList();
    }

    // -------------------------------------------------------
    // ANNOUNCEMENTS (broadcast messaging) — renter-scoped
    //
    // The renter is resolved from the authenticated user; the query
    // service tenant-scopes everything to the renter's own landlord.
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RenterAnnouncementResponse> getAnnouncements(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return announcementQueryService.renterAnnouncements(profile.getTenantId(), profile.getId());
    }

    @Transactional
    public RenterAnnouncementResponse markAnnouncementRead(UUID userId, UUID announcementId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return announcementQueryService.markRead(profile.getTenantId(), profile.getId(), announcementId);
    }

    @Transactional(readOnly = true)
    public long getUnreadAnnouncementsCount(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return announcementQueryService.unreadCount(profile.getTenantId(), profile.getId());
    }

    // -------------------------------------------------------
    // WHATSAPP PREFERENCES — explicit renter consent
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public WhatsAppOptInResponse getWhatsAppOptIn(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        return new WhatsAppOptInResponse(profile.isWhatsAppOptIn());
    }

    @Transactional
    public WhatsAppOptInResponse updateWhatsAppOptIn(UUID userId, boolean enabled) {
        TenantProfile profile = resolveTenantProfile(userId);
        profile.updateWhatsAppOptIn(enabled);
        tenantProfileRepository.save(profile);
        return new WhatsAppOptInResponse(profile.isWhatsAppOptIn());
    }

    /**
     * When the next rent charge will fall due, for a period the scheduler has
     * not posted yet.
     *
     * <p>Mirrors {@code RentChargeScheduler.postDueChargesForLease} exactly:
     * that scheduler bills <strong>full calendar months, 1st to last day,
     * with the due date equal to the period start</strong>, and it resumes
     * from the month after whatever was last posted (or the month after the
     * lease's opening month if nothing has been posted at all). Any other
     * rule here — an anniversary of the move-in date, say — would put a date
     * on the renter's dashboard that the system will never actually charge
     * on, which is worse than the "Not scheduled" it replaces.
     *
     * <p>Returns null rather than guessing when there is no next charge to
     * predict: a tenancy that has ended has no future rent, and neither does
     * a lease whose next period would start after its own end date.
     */
    private LocalDate projectNextDueDate(Lease lease, List<RentLedgerEntry> entries) {
        if (!CURRENT_LEASE_STATUSES.contains(lease.getStatus())) {
            return null;
        }

        YearMonth nextPeriod = entries.stream()
                .map(RentLedgerEntry::getBillingPeriodStart)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .map(latest -> YearMonth.from(latest).plusMonths(1))
                .orElseGet(() -> YearMonth.from(lease.getStartDate()).plusMonths(1));

        LocalDate dueDate = nextPeriod.atDay(1);

        // Never promise rent beyond the end of the tenancy.
        if (lease.getEndDate() != null && dueDate.isAfter(lease.getEndDate())) {
            return null;
        }
        return dueDate;
    }

    /**
     * The renter's current lease, required to be ACTIVE.
     *
     * <p>Use this for anything that <em>acts</em> — initiating a payment,
     * arming auto-pay. Collecting rent against an expired tenancy is not a
     * thing this system should do quietly, so those paths must keep failing
     * closed.
     *
     * <p>For anything that only <em>reads</em>, use
     * {@link #findLeaseForReadAccess} instead. See its javadoc for why the
     * distinction matters.
     */
    private Lease findActiveLease(UUID landlordTenantId, UUID tenantProfileId) {
        return leaseRepository.findAllByTenantAndTenantProfile(landlordTenantId, tenantProfileId)
                .stream()
                .filter(l -> CURRENT_LEASE_STATUSES.contains(l.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RentLedgerStateException("No current lease found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * The renter's current lease if they have one, otherwise their most
     * recent — used by every read-only path.
     *
     * <p>FIX (2026-09-03): every renter endpoint used to require an ACTIVE
     * lease, and {@code LeaseActionScheduler.runDailyExpiry()} expires leases
     * automatically at 01:30. So on the night a tenancy ended, the renter
     * silently lost their payment history, their receipts, and their deposit
     * — and the portal told them "your lease will appear here once your
     * reservation is confirmed", which is the copy for someone who has not
     * moved in yet.
     *
     * <p>The deposit case was the sharpest: the lease page promises a deposit
     * is "refunded, minus any lawful deductions, after move-out inspection",
     * but {@code getDeposit} required an ACTIVE lease, so a renter could
     * never actually watch that refund land. The feature contradicted itself.
     *
     * <p>A former renter keeping read access to their own payment record is
     * not a nicety. It is the evidence they need for a deposit dispute, a
     * reference for the next landlord, or their own tax records.
     */
    private Lease findLeaseForReadAccess(UUID landlordTenantId, UUID tenantProfileId) {
        List<Lease> leases = leaseRepository.findAllByTenantAndTenantProfile(landlordTenantId, tenantProfileId);
        return leases.stream()
                .filter(l -> CURRENT_LEASE_STATUSES.contains(l.getStatus()))
                .findFirst()
                // findAllByTenantAndTenantProfile returns newest-first, so the
                // head is the most recent tenancy when none is active.
                .or(() -> leases.stream().findFirst())
                .orElseThrow(() -> new RentLedgerStateException("No lease found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    /**
     * Batch-fetches the {@code RentLedgerEntry} each of the given
     * transactions was posted against, keyed by entry id, so a page of
     * transactions can be mapped to {@link PaymentHistoryItem} with one
     * query instead of one per row. Every {@code RentTransaction} carries a
     * mandatory, non-null {@code ledgerEntryId} (enforced in the domain
     * constructor), so this should always resolve — a miss is handled
     * defensively in {@link #toPaymentHistoryItem}, not assumed impossible.
     */
    private Map<UUID, RentLedgerEntry> loadLedgerEntriesFor(UUID tenantId, List<RentTransaction> txns) {
        List<UUID> entryIds = txns.stream()
                .map(RentTransaction::getLedgerEntryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return rentLedgerEntryRepository.findAllByTenantAndIdIn(tenantId, entryIds).stream()
                .collect(Collectors.toMap(RentLedgerEntry::getId, e -> e));
    }

    /**
     * FIX (2026-09-03): {@code status} was previously set to
     * {@code txn.getType().name()} — literally a copy of {@code type} — so
     * every row in a tenant's payment history showed its Status column as
     * "Rent Charge" / "Payment" / etc. instead of the entry's actual
     * payment state (PAID / PARTIALLY_PAID / OVERDUE / DUE). Both
     * {@code billingPeriodStart} and {@code billingPeriodEnd} were also
     * hardcoded to {@code ""}, unconditionally, so the Period column read
     * "— – —" for every renter regardless of which month a charge belonged
     * to. Both are now read from the linked {@code RentLedgerEntry} — the
     * thing that actually carries a payment status and a billing period; a
     * transaction (a single payment/charge event) does not.
     */
    private PaymentHistoryItem toPaymentHistoryItem(RentTransaction txn, Map<UUID, RentLedgerEntry> entriesById) {
        String mpesaRef = txn.getSource() == RentTransactionSource.MPESA ? txn.getExternalReference() : null;
        RentLedgerEntry entry = entriesById.get(txn.getLedgerEntryId());
        return new PaymentHistoryItem(
                txn.getId(),
                txn.getType().name(),
                txn.getAmount(),
                txn.getSource().name(),
                txn.getExternalReference(),
                txn.getOccurredAt() != null ? txn.getOccurredAt().toString() : "",
                entry != null ? entry.getStatus().name() : txn.getType().name(),
                entry != null && entry.getBillingPeriodStart() != null ? entry.getBillingPeriodStart().toString() : "",
                entry != null && entry.getBillingPeriodEnd() != null ? entry.getBillingPeriodEnd().toString() : "",
                mpesaRef
        );
    }

    @Transactional
    public RentPaymentRequestResponse initiateRentPayment(UUID userId, UUID entryId, String mpesaPhone) {
        stkPushRateLimiter.checkAndRecord(userId);
        TenantProfile profile = resolveTenantProfile(userId);
        UUID tenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(tenantId, profile.getId());
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException("Entry not found", ErrorCode.RESOURCE_NOT_FOUND));
        if (!entry.getLeaseId().equals(activeLease.getId())) {
            throw new RentLedgerStateException("Entry does not belong to your active lease", ErrorCode.RESOURCE_NOT_FOUND);
        }
        String normalisedPhone = com.rentmanager.shared.phone.KenyanMsisdn.toE164(mpesaPhone);
        RentPaymentRequest request = rentPaymentInitiationService.initiate(tenantId, entryId, normalisedPhone);
        return RentPaymentRequestResponse.from(request);
    }

    @Transactional
    public RentPaymentRequestResponse initiatePortalPayment(UUID userId, BigDecimal amount, String mpesaPhone) {
        stkPushRateLimiter.checkAndRecord(userId);
        TenantProfile profile = resolveTenantProfile(userId);
        UUID tenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(tenantId, profile.getId());

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(tenantId, activeLease.getId())
                .stream()
                .sorted(Comparator.comparing(RentLedgerEntry::getBillingPeriodStart))
                .toList();

        RentLedgerEntry targetEntry;
        List<RentLedgerEntry> unpaid = entries.stream()
                .filter(e -> e.getStatus().isOutstanding())
                .toList();

        if (!unpaid.isEmpty()) {
            targetEntry = unpaid.get(0);
        } else if (!entries.isEmpty()) {
            targetEntry = entries.get(entries.size() - 1);
        } else {
            throw new RentLedgerStateException("No ledger entries found for your lease", ErrorCode.RESOURCE_NOT_FOUND);
        }

        String normalisedPhone = com.rentmanager.shared.phone.KenyanMsisdn.toE164(mpesaPhone);
        RentPaymentRequest request = rentPaymentInitiationService.initiateWithAmount(
                tenantId, targetEntry.getId(), amount, normalisedPhone);
        return RentPaymentRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public RentPaymentRequestResponse getPaymentRequestStatus(UUID userId, UUID requestId) {
        UUID tenantId = resolveTenantProfile(userId).getTenantId();
        RentPaymentRequest request = rentPaymentRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException("Payment request not found", ErrorCode.RESOURCE_NOT_FOUND));

        if (request.getStatus() == RentPaymentRequestStatus.PAID && request.getMpesaReceiptNumber() != null) {
            return rentTransactionRepository.findByExternalReference(tenantId, request.getMpesaReceiptNumber())
                    .map(txn -> RentPaymentRequestResponse.from(request, txn.getId()))
                    .orElse(RentPaymentRequestResponse.from(request));
        }

        return RentPaymentRequestResponse.from(request);
    }
// -------------------------------------------------------
    // AUTO-PAY METHODS
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public AutoPaySettingsResponse getAutoPaySettings(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        Lease activeLease = findActiveLease(profile.getTenantId(), profile.getId());
        AutoPaySettings settings = autoPayService.getSettings(profile.getTenantId(), activeLease.getId())
                .orElse(AutoPaySettings.create(profile.getTenantId(), activeLease.getId(), profile.getId(), ""));
        return AutoPaySettingsResponse.from(settings);
    }

    @Transactional
    public AutoPaySettingsResponse toggleAutoPay(UUID userId, boolean enable, String mpesaPhone) {
        TenantProfile profile = resolveTenantProfile(userId);
        Lease activeLease = findActiveLease(profile.getTenantId(), profile.getId());
        AutoPaySettings settings = autoPayService.toggle(
                profile.getTenantId(), activeLease.getId(), profile.getId(), enable, mpesaPhone);
        return AutoPaySettingsResponse.from(settings);
    }

    @Transactional
    public AutoPaySettingsResponse updateAutoPayPhone(UUID userId, String mpesaPhone) {
        TenantProfile profile = resolveTenantProfile(userId);
        Lease activeLease = findActiveLease(profile.getTenantId(), profile.getId());
        AutoPaySettings settings = autoPayService.updatePhone(
                profile.getTenantId(), activeLease.getId(), mpesaPhone);
        return AutoPaySettingsResponse.from(settings);
    }
}
