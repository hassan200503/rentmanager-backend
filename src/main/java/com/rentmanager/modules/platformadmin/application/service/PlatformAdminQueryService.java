package com.rentmanager.modules.platformadmin.application.service;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.platformadmin.api.dto.response.AdminOverviewResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordDetailResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordSummaryResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.PropertyDetailResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.UserTypeSnapshot;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.PaymentRequestStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdMoney;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.repository.AdminReadModelRepository;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.repository.AdminTenantJpaRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.infrastructure.persistence.repository.UserJpaRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates the platform-wide read model for Super Admins. Every method is
 * intentionally unscoped: the platform owner reads across all landlords and
 * is the only role with access to these queries.
 *
 * Money semantics follow the collection flow: {@code gmv} = sum of rent
 * transaction amounts; {@code commission} = the platform share retained on
 * those transactions (commission_rate_percent × amount). "Current month" is
 * the calendar month via transaction {@code occurred_at}.
 */
@Slf4j
@Service
public class PlatformAdminQueryService {

    private static final long RECENT_TRANSACTIONS_LIMIT = 20;

    private final AdminReadModelRepository adminReadModelRepository;
    private final AdminTenantJpaRepository adminTenantJpaRepository;
    private final com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository propertyJpaRepository;
    private final CommissionPolicyService commissionPolicyService;
    private final PlatformAdminCommissionService platformAdminCommissionService;
    private final String darajaBaseUrl;
    private final com.rentmanager.modules.rentledger.application.scheduler.DisbursementRetrySweepService disbursementRetryService;
    private final UserJpaRepository userJpaRepository;
    private final TenantProfileJpaRepository tenantProfileJpaRepository;

    /**
     * Platform admin action: change a landlord's account status (activate/suspend).
     * Deactivation is intentionally not exposed here — it's a permanent operation
     * that warrants a different flow and confirmation.
     */
    @Transactional
    public void updateLandlordStatus(UUID landlordId, TenantStatus newStatus) {
        TenantEntity tenant = adminTenantJpaRepository.findById(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord not found: " + landlordId,
                        ErrorCode.RESOURCE_NOT_FOUND));

        switch (newStatus) {
            case ACTIVE -> {
                tenant.setStatus(TenantStatus.ACTIVE);
                tenant.setActive(true);
            }
            case SUSPENDED -> {
                tenant.setStatus(TenantStatus.SUSPENDED);
                tenant.setActive(false);
            }
            case DEACTIVATED -> throw new IllegalArgumentException(
                    "Deactivation must use a dedicated endpoint with confirmation");
            default -> throw new IllegalArgumentException("Unsupported status: " + newStatus);
        }

        adminTenantJpaRepository.save(tenant);

        log.info("Platform admin updated landlord {} status to {}", landlordId, newStatus);
    }

    /**
     * Platform-wide disbursement queue: all disbursements across all landlords,
     * filterable by status and landlord. Used by the admin disbursements page.
     */
    @Transactional(readOnly = true)
    public Page<DisbursementJpaEntity> getDisbursements(
            UUID landlordId,
            DisbursementStatus status,
            Boolean requiresManualAttention,
            Pageable pageable) {

        if (landlordId != null) {
            if (status != null) {
                return adminReadModelRepository.findDisbursementsByTenantAndStatus(
                        landlordId, status, pageable);
            }
            return adminReadModelRepository.findDisbursementsByTenant(landlordId, pageable);
        }

        if (requiresManualAttention != null && requiresManualAttention) {
            return adminReadModelRepository.findDisbursementsRequiringManualAttention(pageable);
        }

        if (status != null) {
            return adminReadModelRepository.findDisbursementsByStatus(status, pageable);
        }

        return adminReadModelRepository.findAllDisbursements(pageable);
    }

    /**
     * Platform admin action: manually retry a failed disbursement.
     */
    @Transactional
    public void retryDisbursement(UUID disbursementId) {
        disbursementRetryService.retryOne(disbursementId);
        log.info("Platform admin manually retried disbursement {}", disbursementId);
    }

    public PlatformAdminQueryService(
            AdminReadModelRepository adminReadModelRepository,
            AdminTenantJpaRepository adminTenantJpaRepository,
            com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository propertyJpaRepository,
            CommissionPolicyService commissionPolicyService,
            PlatformAdminCommissionService platformAdminCommissionService,
            com.rentmanager.modules.rentledger.application.scheduler.DisbursementRetrySweepService disbursementRetryService,
            @Value("${daraja.base-url:https://api.safaricom.co.ke}") String darajaBaseUrl,
            UserJpaRepository userJpaRepository,
            TenantProfileJpaRepository tenantProfileJpaRepository) {
        this.adminReadModelRepository = adminReadModelRepository;
        this.adminTenantJpaRepository = adminTenantJpaRepository;
        this.propertyJpaRepository = propertyJpaRepository;
        this.commissionPolicyService = commissionPolicyService;
        this.platformAdminCommissionService = platformAdminCommissionService;
        this.disbursementRetryService = disbursementRetryService;
        this.darajaBaseUrl = darajaBaseUrl;
        this.userJpaRepository = userJpaRepository;
        this.tenantProfileJpaRepository = tenantProfileJpaRepository;
    }

/**
     * Identity snapshot for the one-time userType backfill (frontend
     * scripts/migrate-user-types.ts). Classifies every identity from LOCAL
     * database truth, mirroring the backend's DB-derived authority:
     *
     *   - local User row with role/tenant link  → landlord
     *   - TenantProfile row (renter identity)   → renter
     *   - neither (authenticated, unbound)      → landlord_pending
     *
     * Platform admins have no DB row (platformRole is a claim, not
     * persisted state) and are therefore NOT present here — the migration
     * script treats unknown ids as "no truth" and skips them.
     */
    @Transactional(readOnly = true)
    public List<UserTypeSnapshot> getUserTypes() {
        // Renter profiles are the ONLY renter-side identity rows. Some
        // renters (fulfillment-created, not yet signed in) have a profile
        // but no User row — the union below guarantees they are still
        // classified, so the backfill never leaves a gap.
        Set<String> renterClerkUserIds =
                new HashSet<>(tenantProfileJpaRepository.findAllClerkUserIds());

        Set<String> seen = new HashSet<>();
        List<UserTypeSnapshot> snapshots = userJpaRepository.findAll().stream()
                .map(user -> {
                    String clerkUserId = user.getClerkUserId();
                    seen.add(clerkUserId);
                    return new UserTypeSnapshot(
                            clerkUserId,
                            classifyUser(clerkUserId, user.getRole(), user.getTenantId(), renterClerkUserIds));
                })
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        for (String renterClerkUserId : renterClerkUserIds) {
            if (!seen.contains(renterClerkUserId)) {
                snapshots.add(new UserTypeSnapshot(renterClerkUserId, "renter"));
            }
        }

        log.info("Platform admin fetched identity snapshot. users={} renters={}",
                snapshots.size(), renterClerkUserIds.size());
        return snapshots;
    }

    private String classifyUser(String clerkUserId, UserRole role, UUID tenantId, Set<String> renterClerkUserIds) {
        if (role != null || tenantId != null) {
            return "landlord";
        }
        return renterClerkUserIds.contains(clerkUserId) ? "renter" : "landlord_pending";
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        long totalTenants = adminTenantJpaRepository.count();

        Map<TenantStatus, Long> tenantStatus = new EnumMap<>(TenantStatus.class);
        adminReadModelRepository.countTenantsByStatus()
                .forEach(c -> {
                    if (c.status() != null) {
                        tenantStatus.put(c.status(), c.count());
                    }
                });

        long properties = adminReadModelRepository.countProperties();
        long units = adminReadModelRepository.countUnits();
        long activeLeases = adminReadModelRepository.countLeasesByStatus(LeaseStatus.ACTIVE);
        long renters = adminReadModelRepository.countRenters();

        Map<RentPaymentRequestStatus, Long> paymentRequests = paymentsByStatus();
        Map<DisbursementStatus, Long> disbursements = disbursementsByStatus();
        long requiresManualAttention = adminReadModelRepository.countDisbursementsRequiringManualAttention();

        YearMonth thisMonth = YearMonth.now();
        LocalDateTime currentMonthStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime previousMonthStart = thisMonth.minusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();

        BigDecimal gmvCurrentMonth =
                adminReadModelRepository.sumAmountBetween(currentMonthStart, nextMonthStart);
        BigDecimal gmvPreviousMonth =
                adminReadModelRepository.sumAmountBetween(previousMonthStart, currentMonthStart);
        BigDecimal commissionCurrentMonth =
                adminReadModelRepository.sumCommissionBetween(currentMonthStart, nextMonthStart);
        BigDecimal commissionPreviousMonth =
                adminReadModelRepository.sumCommissionBetween(previousMonthStart, currentMonthStart);

        BigDecimal defaultRate = commissionPolicyService.getActiveRate(null);
        boolean sandbox = darajaBaseUrl != null && darajaBaseUrl.toLowerCase().contains("sandbox");

        return new AdminOverviewResponse(
                new AdminOverviewResponse.PlatformStats(
                        totalTenants,
                        tenantStatus.getOrDefault(TenantStatus.ACTIVE, 0L),
                        tenantStatus.getOrDefault(TenantStatus.SUSPENDED, 0L),
                        tenantStatus.getOrDefault(TenantStatus.PENDING, 0L),
                        tenantStatus.getOrDefault(TenantStatus.DEACTIVATED, 0L),
                        properties, units, activeLeases, renters, defaultRate),
                new AdminOverviewResponse.PaymentStats(
                        gmvCurrentMonth, gmvPreviousMonth, commissionCurrentMonth, commissionPreviousMonth,
                        paymentRequests.getOrDefault(RentPaymentRequestStatus.PENDING, 0L),
                        paymentRequests.getOrDefault(RentPaymentRequestStatus.PAID, 0L),
                        paymentRequests.getOrDefault(RentPaymentRequestStatus.FAILED, 0L)),
                new AdminOverviewResponse.DisbursementStats(
                        disbursements.getOrDefault(DisbursementStatus.INITIATED, 0L),
                        disbursements.getOrDefault(DisbursementStatus.PENDING, 0L),
                        disbursements.getOrDefault(DisbursementStatus.SUCCESS, 0L),
                        disbursements.getOrDefault(DisbursementStatus.FAILED, 0L),
                        requiresManualAttention),
                new AdminOverviewResponse.EnvironmentInfo(sandbox ? "SANDBOX" : "PRODUCTION", sandbox)
        );
    }

    @Transactional(readOnly = true)
    public Page<LandlordSummaryResponse> getLandlords(String search, Pageable pageable) {
        Page<TenantEntity> tenants = (search == null || search.isBlank())
                ? adminTenantJpaRepository.findAll(pageable)
                : adminTenantJpaRepository.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search.trim(), search.trim(), search.trim(), pageable);

        Map<UUID, Long> propertiesByTenant = tenantIdCountMap(adminReadModelRepository.countPropertiesByTenantGrouped());
        Map<UUID, Long> unitsByTenant = tenantIdCountMap(adminReadModelRepository.countUnitsByTenantGrouped());
        Map<UUID, Long> leasesByTenant = tenantIdCountMap(
                adminReadModelRepository.countLeasesByTenantGroupedByStatus(LeaseStatus.ACTIVE));
        Map<UUID, Long> rentersByTenant = tenantIdCountMap(adminReadModelRepository.countRentersByTenantGrouped());

        Map<UUID, TenantIdMoney> moneyByTenant = new HashMap<>();
        adminReadModelRepository.sumTransactionAmountsByTenantGrouped()
                .forEach(m -> moneyByTenant.put(m.tenantId(), m));

        Map<UUID, LocalDateTime> lastActivityByTenant = new HashMap<>();
        adminReadModelRepository.lastTransactionDateByTenantGrouped()
                .forEach(a -> lastActivityByTenant.put(a.tenantId(), a.lastActivityAt()));

        return tenants.map(tenant -> toSummary(
                tenant, propertiesByTenant, unitsByTenant, leasesByTenant,
                rentersByTenant, moneyByTenant, lastActivityByTenant));
    }

    private LandlordSummaryResponse toSummary(
            TenantEntity tenant,
            Map<UUID, Long> propertiesByTenant,
            Map<UUID, Long> unitsByTenant,
            Map<UUID, Long> leasesByTenant,
            Map<UUID, Long> rentersByTenant,
            Map<UUID, TenantIdMoney> moneyByTenant,
            Map<UUID, LocalDateTime> lastActivityByTenant) {

        TenantIdMoney money = moneyByTenant.get(tenant.getId());
        UUID tenantId = tenant.getId();
        return new LandlordSummaryResponse(
                tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getEmail(),
                tenant.getBillingMode(), tenant.getStatus(), tenant.getCreatedAt(),
                propertiesByTenant.getOrDefault(tenantId, 0L),
                unitsByTenant.getOrDefault(tenantId, 0L),
                leasesByTenant.getOrDefault(tenantId, 0L),
                rentersByTenant.getOrDefault(tenantId, 0L),
                money == null ? BigDecimal.ZERO : money.amount(),
                money == null ? BigDecimal.ZERO : money.commissionAmount(),
                lastActivityByTenant.get(tenantId),
                commissionPolicyService.getActiveRate(tenantId));
    }

    @Transactional(readOnly = true)
    public LandlordDetailResponse getLandlordDetail(UUID landlordId) {
        TenantEntity tenant = adminTenantJpaRepository.findById(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord not found: " + landlordId, ErrorCode.RESOURCE_NOT_FOUND));

        List<PropertyJpaEntity> properties = adminReadModelRepository.findPropertiesByTenant(landlordId);
        List<UnitJpaEntity> units = adminReadModelRepository.findUnitsByTenant(landlordId);
        List<LeaseEntity> leases = adminReadModelRepository.findLeasesByTenant(landlordId);
        List<TenantProfileEntity> renters = adminReadModelRepository.findTenantProfilesByTenant(landlordId);
        List<RentPaymentRequestJpaEntity> paymentRequests = adminReadModelRepository.findPaymentRequestsByTenant(landlordId);
        List<DisbursementJpaEntity> disbursements = adminReadModelRepository.findDisbursementsByTenant(landlordId);
        List<RentTransactionJpaEntity> recentTransactions = adminReadModelRepository.findRecentTransactionsByTenant(
                landlordId, PageRequest.of(0, (int) RECENT_TRANSACTIONS_LIMIT));

        Map<UUID, LocalDateTime> lastActivityByTenant = new HashMap<>();
        adminReadModelRepository.lastTransactionDateByTenantGrouped()
                .forEach(a -> lastActivityByTenant.put(a.tenantId(), a.lastActivityAt()));
        LocalDateTime lastActivityAt = lastActivityByTenant.get(landlordId);

        Map<UUID, List<UnitJpaEntity>> unitsByProperty = units.stream()
                .collect(Collectors.groupingBy(UnitJpaEntity::getPropertyId));

        List<LandlordDetailResponse.PropertySummary> propertySummaries = properties.stream()
                .map(p -> propertySummary(p, unitsByProperty.getOrDefault(p.getId(), List.of())))
                .toList();

        List<LandlordDetailResponse.RenterSummary> renterSummaries = renters.stream()
                .map(r -> new LandlordDetailResponse.RenterSummary(
                        r.getId(), r.getFullName(), r.getEmail(), r.getPhone(), r.getNationalId()))
                .toList();

        List<LandlordDetailResponse.LeaseSummary> leaseSummaries = leases.stream()
                .map(l -> new LandlordDetailResponse.LeaseSummary(
                        l.getId(), l.getLeaseNumber(), l.getStatus(), l.getPropertyId(), l.getUnitId(),
                        l.getStartDate(), l.getEndDate(), l.getRentAmount()))
                .toList();

        List<LandlordDetailResponse.PaymentRequestSummary> paymentSummaries = paymentRequests.stream()
                .map(p -> new LandlordDetailResponse.PaymentRequestSummary(
                        p.getId(), p.getAmount(), p.getStatus(), p.getMpesaReceiptNumber(), p.getCreatedAt()))
                .toList();

        List<LandlordDetailResponse.DisbursementSummary> disbursementSummaries = disbursements.stream()
                .map(d -> new LandlordDetailResponse.DisbursementSummary(
                        d.getId(), d.getAmount(), d.getRecipientName(), d.getStatus(),
                        d.isRequiresManualAttention(), d.getCreatedAt()))
                .toList();

        List<LandlordDetailResponse.RentTransactionSummary> transactionSummaries = recentTransactions.stream()
                .map(t -> new LandlordDetailResponse.RentTransactionSummary(
                        t.getId(), t.getAmount(), t.getCommissionAmount(), t.getSource(), t.getOccurredAt()))
                .toList();

        TenantIdMoney money = adminReadModelRepository.sumTransactionAmountsByTenantGrouped().stream()
                .filter(m -> m.tenantId().equals(landlordId))
                .findFirst()
                .orElse(null);
        var commission = platformAdminCommissionService.getCommissionFor(landlordId);

        return new LandlordDetailResponse(
                tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getEmail(), tenant.getPhoneNumber(),
                tenant.getStatus(), tenant.getBillingMode(), tenant.getCreatedAt(),
                lastActivityAt,
                money == null ? BigDecimal.ZERO : money.amount(),
                money == null ? BigDecimal.ZERO : money.commissionAmount(),
                commission.ratePercent(), commission.source(),
                propertySummaries, renterSummaries, leaseSummaries, paymentSummaries,
                disbursementSummaries, transactionSummaries);
    }

    /**
     * Platform-wide property search: all properties across all landlords,
     * filterable by search text (name/reference) and landlord. Used by the admin
     * properties page.
     */
    @Transactional(readOnly = true)
    public Page<com.rentmanager.modules.platformadmin.api.dto.response.PropertySummaryResponse> getProperties(
            String search,
            UUID landlordId,
            Pageable pageable) {

        Page<PropertyJpaEntity> properties;

        if (landlordId != null && search != null && !search.isBlank()) {
            properties = adminReadModelRepository.findPropertiesByLandlordAndSearch(
                    landlordId, search.trim(), pageable);
        } else if (landlordId != null) {
            properties = adminReadModelRepository.findPropertiesByLandlord(landlordId, pageable);
        } else if (search != null && !search.isBlank()) {
            properties = adminReadModelRepository.findPropertiesBySearch(search.trim(), pageable);
        } else {
            properties = adminReadModelRepository.findAllProperties(pageable);
        }

        // Load tenant info for attribution
        Map<UUID, TenantEntity> tenantsById = properties.getContent().stream()
                .map(PropertyJpaEntity::getTenantId)
                .distinct()
                .map(id -> adminTenantJpaRepository.findById(id).orElse(null))
                .filter(t -> t != null)
                .collect(Collectors.toMap(TenantEntity::getId, t -> t));

        // Load unit counts per property
        List<UUID> propertyIds = properties.getContent().stream()
                .map(PropertyJpaEntity::getId)
                .toList();

        Map<UUID, Long> unitsPerProperty = new HashMap<>();
        Map<UUID, Long> occupiedPerProperty = new HashMap<>();

        if (!propertyIds.isEmpty()) {
            List<UnitJpaEntity> units = adminReadModelRepository.findUnitsByPropertyIds(propertyIds);

            // Group units by property
            Map<UUID, List<UnitJpaEntity>> unitsByProperty = units.stream()
                    .collect(Collectors.groupingBy(UnitJpaEntity::getPropertyId));

            // Count total and occupied units per property
            for (Map.Entry<UUID, List<UnitJpaEntity>> entry : unitsByProperty.entrySet()) {
                UUID propertyId = entry.getKey();
                List<UnitJpaEntity> propertyUnits = entry.getValue();
                unitsPerProperty.put(propertyId, (long) propertyUnits.size());
                occupiedPerProperty.put(propertyId,
                        propertyUnits.stream()
                                .filter(u -> u.getOccupancyStatus() == UnitOccupancyStatus.OCCUPIED)
                                .count());
            }
        }

        return properties.map(property -> {
            TenantEntity tenant = tenantsById.get(property.getTenantId());
            return new com.rentmanager.modules.platformadmin.api.dto.response.PropertySummaryResponse(
                    property.getId(),
                    property.getReferenceCode(),
                    property.getName(),
                    property.getStatus(),
                    property.getPropertyType(),
                    property.getPremisesType(),
                    unitsPerProperty.getOrDefault(property.getId(), 0L),
                    occupiedPerProperty.getOrDefault(property.getId(), 0L),
                    property.getCreatedAt(),
                    tenant != null ? tenant.getId() : null,
                    tenant != null ? tenant.getName() : "Unknown",
                    tenant != null ? tenant.getSlug() : null
            );
        });
    }

    /**
     * Property detail view for {@code GET /api/v1/admin/properties/{id}}.
     * Returns full property details with units, landlord info, and rental history.
     */
    @Transactional(readOnly = true)
    public PropertyDetailResponse getPropertyDetail(UUID propertyId) {
        // Fetch property
        PropertyJpaEntity property = adminReadModelRepository.findPropertyById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found: " + propertyId,
                        ErrorCode.RESOURCE_NOT_FOUND));

        // Fetch landlord
        TenantEntity landlord = adminTenantJpaRepository.findById(property.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord not found: " + property.getTenantId(),
                        ErrorCode.RESOURCE_NOT_FOUND));

        // Fetch units
        List<UnitJpaEntity> units = adminReadModelRepository.findUnitsByPropertyIds(List.of(propertyId));

        // Fetch leases
        List<LeaseEntity> allLeases = adminReadModelRepository.findLeasesByTenant(property.getTenantId())
                .stream()
                .filter(l -> l.getPropertyId().equals(propertyId))
                .toList();

        // Split into active and past leases
        List<LeaseEntity> activeLeases = allLeases.stream()
                .filter(l -> l.getStatus() == LeaseStatus.ACTIVE || 
                             l.getStatus() == LeaseStatus.PENDING_ACTIVATION ||
                             l.getStatus() == LeaseStatus.AWAITING_DEPOSIT)
                .toList();

        List<LeaseEntity> pastLeases = allLeases.stream()
                .filter(l -> l.getStatus() == LeaseStatus.EXPIRED || l.getStatus() == LeaseStatus.TERMINATED)
                .toList();

        // Fetch renter profiles for lease attribution
        List<UUID> renterIds = allLeases.stream()
                .map(LeaseEntity::getTenantProfileId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        Map<UUID, TenantProfileEntity> rentersById = new HashMap<>();
        if (!renterIds.isEmpty()) {
            adminReadModelRepository.findTenantProfilesByTenant(property.getTenantId())
                    .stream()
                    .filter(r -> renterIds.contains(r.getId()))
                    .forEach(r -> rentersById.put(r.getId(), r));
        }

        // Create unit map for lease lookup
        Map<UUID, UnitJpaEntity> unitsById = units.stream()
                .collect(Collectors.toMap(UnitJpaEntity::getId, u -> u));

        // Map units
        List<PropertyDetailResponse.UnitSummary> unitSummaries = units.stream()
                .map(u -> new PropertyDetailResponse.UnitSummary(
                        u.getId(),
                        u.getUnitNumber(),
                        u.getLabel(),
                        u.getStatus(),
                        u.getOccupancyStatus(),
                        u.getRentAmount(),
                        u.getDepositAmount(),
                        u.getFloor()
                ))
                .toList();

        // Map active leases
        List<PropertyDetailResponse.LeaseSummary> activeLeaseSummaries = activeLeases.stream()
                .map(l -> toLeaseSummary(l, rentersById, unitsById))
                .toList();

        // Map past leases
        List<PropertyDetailResponse.LeaseSummary> pastLeaseSummaries = pastLeases.stream()
                .map(l -> toLeaseSummary(l, rentersById, unitsById))
                .toList();

        // Build address
        PropertyDetailResponse.AddressInfo addressInfo = null;
        if (property.getAddress() != null) {
            var addr = property.getAddress();
            addressInfo = new PropertyDetailResponse.AddressInfo(
                    addr.getAddressLine1(),
                    addr.getCity(),
                    addr.getState(),
                    addr.getPostalCode(),
                    addr.getCountry()
            );
        }

        // Build landlord info
        PropertyDetailResponse.LandlordInfo landlordInfo = new PropertyDetailResponse.LandlordInfo(
                landlord.getId(),
                landlord.getName(),
                landlord.getSlug(),
                landlord.getEmail(),
                landlord.getStatus(),
                landlord.getBillingMode()
        );

        long occupiedCount = units.stream()
                .filter(u -> u.getOccupancyStatus() == UnitOccupancyStatus.OCCUPIED)
                .count();

        return new PropertyDetailResponse(
                property.getId(),
                property.getReferenceCode(),
                property.getName(),
                property.getDescription(),
                property.getStatus(),
                property.getPropertyType(),
                property.getPremisesType(),
                property.getPremisesTypeOverrideReason(),
                property.getOccupancyStatus(),
                property.getCreatedAt(),
                property.getUpdatedAt(),
                addressInfo,
                landlordInfo,
                units.size(),
                occupiedCount,
                unitSummaries,
                activeLeaseSummaries,
                pastLeaseSummaries
        );
    }

    private PropertyDetailResponse.LeaseSummary toLeaseSummary(
            LeaseEntity lease,
            Map<UUID, TenantProfileEntity> rentersById,
            Map<UUID, UnitJpaEntity> unitsById) {
        TenantProfileEntity renter = rentersById.get(lease.getTenantProfileId());
        UnitJpaEntity unit = unitsById.get(lease.getUnitId());
        return new PropertyDetailResponse.LeaseSummary(
                lease.getId(),
                lease.getLeaseNumber(),
                lease.getStatus(),
                lease.getUnitId(),
                unit != null ? unit.getUnitNumber() : "Unknown",
                lease.getTenantProfileId(),
                renter != null ? renter.getFullName() : "Unknown",
                renter != null ? renter.getEmail() : null,
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getRentAmount(),
                lease.getCreatedAt()
        );
    }

    /**
     * Platform-wide renter list: all renters across all landlords, filterable
     * by search (name/email/phone) and landlord. Includes active lease status
     * for each renter. Used by the admin renters page.
     */
    @Transactional(readOnly = true)
    public Page<com.rentmanager.modules.platformadmin.api.dto.response.RenterSummaryResponse> getRenters(
            String search,
            UUID landlordId,
            Pageable pageable) {

        Page<TenantProfileEntity> renters;

        if (landlordId != null && search != null && !search.isBlank()) {
            renters = adminReadModelRepository.findRentersByLandlordAndSearch(
                    landlordId, search.trim(), pageable);
        } else if (landlordId != null) {
            renters = adminReadModelRepository.findRentersByLandlord(landlordId, pageable);
        } else if (search != null && !search.isBlank()) {
            renters = adminReadModelRepository.findRentersBySearch(search.trim(), pageable);
        } else {
            renters = adminReadModelRepository.findAllRenters(pageable);
        }

        // Load tenant info for attribution
        Map<UUID, TenantEntity> tenantsById = renters.getContent().stream()
                .map(TenantProfileEntity::getTenantId)
                .distinct()
                .map(id -> adminTenantJpaRepository.findById(id).orElse(null))
                .filter(t -> t != null)
                .collect(Collectors.toMap(TenantEntity::getId, t -> t));

        // Load active leases for each renter
        Map<UUID, LeaseEntity> activeLeasesById = new HashMap<>();
        for (TenantProfileEntity renter : renters.getContent()) {
            List<LeaseEntity> activeLeases = adminReadModelRepository
                    .findLeasesByTenantProfileIdAndStatus(renter.getId(), LeaseStatus.ACTIVE);
            if (!activeLeases.isEmpty()) {
                activeLeasesById.put(renter.getId(), activeLeases.get(0));
            }
        }

        return renters.map(renter -> {
            TenantEntity tenant = tenantsById.get(renter.getTenantId());
            LeaseEntity activeLease = activeLeasesById.get(renter.getId());
            return new com.rentmanager.modules.platformadmin.api.dto.response.RenterSummaryResponse(
                    renter.getId(),
                    renter.getFullName(),
                    renter.getEmail(),
                    renter.getPhone(),
                    renter.getNationalId(),
                    tenant != null ? tenant.getId() : null,
                    tenant != null ? tenant.getName() : "Unknown",
                    tenant != null ? tenant.getSlug() : null,
                    activeLease != null ? activeLease.getId() : null,
                    activeLease != null ? activeLease.getStatus() : null
            );
        });
    }

    private Map<RentPaymentRequestStatus, Long> paymentsByStatus() {
        Map<RentPaymentRequestStatus, Long> counts = new EnumMap<>(RentPaymentRequestStatus.class);
        adminReadModelRepository.countPaymentRequestsByStatus().stream()
                .filter(c -> c.status() != null)
                .collect(Collectors.toMap(PaymentRequestStatusCount::status, PaymentRequestStatusCount::count,
                        Long::sum, () -> counts));
        return counts;
    }

    private Map<DisbursementStatus, Long> disbursementsByStatus() {
        Map<DisbursementStatus, Long> counts = new EnumMap<>(DisbursementStatus.class);
        adminReadModelRepository.countDisbursementsByStatus()
                .forEach(c -> {
                    if (c.status() != null) {
                        counts.put(c.status(), c.count());
                    }
                });
        return counts;
    }

    private static Map<UUID, Long> tenantIdCountMap(List<TenantIdCount> rows) {
        Map<UUID, Long> map = new HashMap<>();
        rows.forEach(row -> map.put(row.tenantId(), row.count()));
        return map;
    }

    private static LandlordDetailResponse.PropertySummary propertySummary(PropertyJpaEntity property, List<UnitJpaEntity> units) {
        long occupied = units.stream()
                .filter(u -> u.getOccupancyStatus() == UnitOccupancyStatus.OCCUPIED)
                .count();
        return new LandlordDetailResponse.PropertySummary(
                property.getId(), property.getReferenceCode(), property.getName(), property.getStatus(),
                property.getPropertyType(), property.getPremisesType(), units.size(), occupied);
    }
}