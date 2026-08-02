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
import com.rentmanager.modules.review.application.ReviewCommandService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
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

    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;
    private final RentPaymentInitiationService rentPaymentInitiationService;
    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final AutoPayService autoPayService;
    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;
    private final MaintenanceRequestCommandService maintenanceRequestCommandService;
    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final AnnouncementQueryService announcementQueryService;

    @Transactional(readOnly = true)
    public TenantDashboardResponse getDashboard(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));
        Property property = propertyRepository.findByIdAndTenantId(unit.getPropertyId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Property not found", ErrorCode.RESOURCE_NOT_FOUND));

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, activeLease.getId());

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

        // First unpaid entry (earliest due date with balance > 0) — used by
        // the Pay Now button to know which entry to charge.
        RentLedgerEntry firstUnpaidEntry = entries.stream()
                .filter(e -> e.getBalanceOwed().compareTo(BigDecimal.ZERO) > 0)
                .filter(e -> e.getStatus() != RentLedgerStatus.PAID && e.getStatus() != RentLedgerStatus.OVERPAID)
                .min(Comparator.comparing(RentLedgerEntry::getDueDate))
                .orElse(null);

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());
        List<RentTransaction> recentPayments = allTxns.stream()
                .filter(t -> t.reducesBalanceOwed() || t.getType() == RentTransactionType.PAYMENT)
                .sorted(Comparator.comparing(RentTransaction::getOccurredAt).reversed())
                .limit(5)
                .toList();

        return new TenantDashboardResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                currentBalance,
                firstUnpaidEntry != null ? firstUnpaidEntry.getId() : null,
                nextDueEntry != null ? nextDueEntry.getDueDate() : null,
                nextDueEntry != null ? nextDueEntry.getAmountDue() : BigDecimal.ZERO,
                overdueAmount,
                activeLease.getStatus().name(),
                unit.getUnitNumber(),
                property.getName(),
                activeLease.getRentAmount(),
                activeLease.getSecurityDeposit() != null ? activeLease.getSecurityDeposit() : BigDecimal.ZERO,
                recentPayments.stream().map(this::toPaymentHistoryItem).toList()
        );
    }

    @Transactional(readOnly = true)
    public TenantLeaseResponse getLease(UUID userId) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID landlordTenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId)
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

        // GRACE_PERIOD (Phase 1): a premium landlord whose renewal payment
        // failed still counts as verified - they remain a paying customer
        // until the grace window ends and the scheduler reverts them.
        boolean landlordVerified = landlord.getSubscriptionStatus() != null
                && (landlord.getSubscriptionStatus() == SubscriptionStatus.ACTIVE
                    || landlord.getSubscriptionStatus() == SubscriptionStatus.TRIAL
                    || landlord.getSubscriptionStatus() == SubscriptionStatus.GRACE_PERIOD);

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

        return new TenantLeaseResponse(
                activeLease.getId(),
                activeLease.getLeaseNumber(),
                activeLease.getStartDate(),
                activeLease.getEndDate(),
                activeLease.getRentAmount(),
                activeLease.getSecurityDeposit() != null ? activeLease.getSecurityDeposit() : BigDecimal.ZERO,
                activeLease.getStatus().name(),
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
                landlord.getCreatedAt() != null ? landlord.getCreatedAt().toString() : null,
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
                landlord.getSubscriptionStatus() != null ? landlord.getSubscriptionStatus().name() : null
        );
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
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE)
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
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());

        BigDecimal totalPaid = allTxns.stream()
                .filter(t -> t.reducesBalanceOwed())
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDue = allTxns.stream()
                .filter(t -> t.getType() == RentTransactionType.RENT_CHARGE)
                .map(RentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(landlordTenantId, activeLease.getId());
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
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());

        List<RentTransaction> allTxns = rentTransactionRepository.findByLease(landlordTenantId, activeLease.getId());
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
            pageContent = sorted.subList(fromIndex, toIndex).stream()
                    .map(this::toPaymentHistoryItem)
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

    private TenantProfile resolveTenantProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RentLedgerStateException("User not found", ErrorCode.RESOURCE_NOT_FOUND));
        String clerkUserId = user.getClerkUserId();
        return tenantProfileRepository.findByClerkUserId(clerkUserId)
                .orElseThrow(() -> new RentLedgerStateException("Tenant profile not found", ErrorCode.RESOURCE_NOT_FOUND));
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
        Lease activeLease = findActiveLease(landlordTenantId, profile.getId());
        Unit unit = unitRepository.findByIdAndTenantId(activeLease.getUnitId(), landlordTenantId)
                .orElseThrow(() -> new RentLedgerStateException("Unit not found", ErrorCode.RESOURCE_NOT_FOUND));

        MaintenanceRequest request = maintenanceRequestCommandService.submit(
                landlordTenantId,
                activeLease.getUnitId(),
                unit.getPropertyId(),
                profile.getId(),
                activeLease.getId(),
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

    private Lease findActiveLease(UUID landlordTenantId, UUID tenantProfileId) {
        List<Lease> allLeases = leaseRepository.findAllByTenant(landlordTenantId);
        return allLeases.stream()
                .filter(l -> l.getTenantProfileId().equals(tenantProfileId))
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new RentLedgerStateException("No active lease found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    private PaymentHistoryItem toPaymentHistoryItem(RentTransaction txn) {
        String mpesaRef = txn.getSource() == RentTransactionSource.MPESA ? txn.getExternalReference() : null;
        return new PaymentHistoryItem(
                txn.getId(),
                txn.getType().name(),
                txn.getAmount(),
                txn.getSource().name(),
                txn.getExternalReference(),
                txn.getOccurredAt() != null ? txn.getOccurredAt().toString() : "",
                txn.getType().name(),
                "",
                "",
                mpesaRef
        );
    }

    @Transactional
    public RentPaymentRequestResponse initiateRentPayment(UUID userId, UUID entryId, String mpesaPhone) {
        TenantProfile profile = resolveTenantProfile(userId);
        UUID tenantId = profile.getTenantId();
        Lease activeLease = findActiveLease(tenantId, profile.getId());
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(entryId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException("Entry not found", ErrorCode.RESOURCE_NOT_FOUND));
        if (!entry.getLeaseId().equals(activeLease.getId())) {
            throw new RentLedgerStateException("Entry does not belong to your active lease", ErrorCode.RESOURCE_NOT_FOUND);
        }
        String normalisedPhone = mpesaPhone.strip();
        if (normalisedPhone.startsWith("07")) {
            normalisedPhone = "+254" + normalisedPhone.substring(1);
        } else if (normalisedPhone.startsWith("254")) {
            normalisedPhone = "+" + normalisedPhone;
        }
        RentPaymentRequest request = rentPaymentInitiationService.initiate(tenantId, entryId, normalisedPhone);
        return RentPaymentRequestResponse.from(request);
    }

    @Transactional
    public RentPaymentRequestResponse initiatePortalPayment(UUID userId, BigDecimal amount, String mpesaPhone) {
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

        String normalisedPhone = mpesaPhone.strip();
        if (normalisedPhone.startsWith("07")) {
            normalisedPhone = "+254" + normalisedPhone.substring(1);
        } else if (normalisedPhone.startsWith("254")) {
            normalisedPhone = "+" + normalisedPhone;
        }
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
