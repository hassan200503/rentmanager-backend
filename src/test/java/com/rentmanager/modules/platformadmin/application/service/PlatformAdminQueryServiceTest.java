package com.rentmanager.modules.platformadmin.application.service;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.platformadmin.api.dto.response.AdminOverviewResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordDetailResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordSummaryResponse;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.DisbursementStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.PaymentRequestStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdLastActivity;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantIdMoney;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.repository.AdminReadModelRepository;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.repository.AdminTenantJpaRepository;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformAdminQueryServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();

    private AdminReadModelRepository readModel;
    private AdminTenantJpaRepository tenantRepo;
    private com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository propertyRepo;
    private CommissionPolicyService commissionPolicyService;
    private PlatformAdminCommissionService commissionService;
    private com.rentmanager.modules.rentledger.application.scheduler.DisbursementRetrySweepService disbursementRetryService;
    private com.rentmanager.modules.user.infrastructure.persistence.repository.UserJpaRepository userRepo;
    private com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository tenantProfileRepo;
    private PlatformAdminQueryService service;

    @BeforeEach
    void setUp() {
        readModel = mock(AdminReadModelRepository.class);
        tenantRepo = mock(AdminTenantJpaRepository.class);
        propertyRepo = mock(com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository.class);
        commissionPolicyService = mock(CommissionPolicyService.class);
        commissionService = mock(PlatformAdminCommissionService.class);
        disbursementRetryService = mock(com.rentmanager.modules.rentledger.application.scheduler.DisbursementRetrySweepService.class);
        userRepo = mock(com.rentmanager.modules.user.infrastructure.persistence.repository.UserJpaRepository.class);
        tenantProfileRepo = mock(com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository.class);
        service = new PlatformAdminQueryService(
                readModel, tenantRepo, propertyRepo, commissionPolicyService, commissionService,
                disbursementRetryService, "https://sandbox.safaricom.co.ke", userRepo, tenantProfileRepo);
    }

    @Test
    void overview_mapsStatusCountsMonthMoneyAndSandboxEnvironment() {
        when(tenantRepo.count()).thenReturn(3L);
        when(readModel.countTenantsByStatus()).thenReturn(List.of(
                new TenantStatusCount(TenantStatus.ACTIVE, 2L),
                new TenantStatusCount(TenantStatus.SUSPENDED, 1L),
                new TenantStatusCount(TenantStatus.PENDING, 1L),
                new TenantStatusCount(TenantStatus.DEACTIVATED, 1L)));
        when(readModel.countProperties()).thenReturn(10L);
        when(readModel.countUnits()).thenReturn(20L);
        when(readModel.countLeasesByStatus(LeaseStatus.ACTIVE)).thenReturn(5L);
        when(readModel.countRenters()).thenReturn(30L);
        when(readModel.countPaymentRequestsByStatus()).thenReturn(List.of(
                new PaymentRequestStatusCount(RentPaymentRequestStatus.PAID, 7L),
                new PaymentRequestStatusCount(RentPaymentRequestStatus.PENDING, 3L),
                new PaymentRequestStatusCount(RentPaymentRequestStatus.FAILED, 1L)));
        when(readModel.countDisbursementsByStatus()).thenReturn(List.of(
                new DisbursementStatusCount(DisbursementStatus.SUCCESS, 4L),
                new DisbursementStatusCount(DisbursementStatus.FAILED, 1L),
                new DisbursementStatusCount(DisbursementStatus.PENDING, 2L)));
        when(readModel.countDisbursementsRequiringManualAttention()).thenReturn(1L);
        when(commissionPolicyService.getActiveRate(null)).thenReturn(new BigDecimal("5.00"));

        LocalDateTime currentStart = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime prevStart = YearMonth.now().minusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime nextStart = YearMonth.now().plusMonths(1).atDay(1).atStartOfDay();
        when(readModel.sumAmountBetween(currentStart, nextStart)).thenReturn(new BigDecimal("1000.00"));
        when(readModel.sumAmountBetween(prevStart, currentStart)).thenReturn(new BigDecimal("800.00"));
        when(readModel.sumCommissionBetween(currentStart, nextStart)).thenReturn(new BigDecimal("50.00"));
        when(readModel.sumCommissionBetween(prevStart, currentStart)).thenReturn(new BigDecimal("40.00"));

        AdminOverviewResponse overview = service.getOverview();

        assertThat(overview.platform().totalTenants()).isEqualTo(3L);
        assertThat(overview.platform().activeTenants()).isEqualTo(2L);
        assertThat(overview.platform().suspendedTenants()).isEqualTo(1L);
        assertThat(overview.platform().totalProperties()).isEqualTo(10L);
        assertThat(overview.platform().activeLeases()).isEqualTo(5L);
        assertThat(overview.platform().platformDefaultCommissionRate()).isEqualByComparingTo("5.00");
        assertThat(overview.payments().gmvCurrentMonth()).isEqualByComparingTo("1000.00");
        assertThat(overview.payments().gmvPreviousMonth()).isEqualByComparingTo("800.00");
        assertThat(overview.payments().commissionCurrentMonth()).isEqualByComparingTo("50.00");
        assertThat(overview.payments().paymentRequestsPaid()).isEqualTo(7L);
        assertThat(overview.disbursements().success()).isEqualTo(4L);
        assertThat(overview.disbursements().requiresManualAttention()).isEqualTo(1L);
        assertThat(overview.environment().environment()).isEqualTo("SANDBOX");
        assertThat(overview.environment().sandbox()).isTrue();
    }

    @Test
    void overview_marksProductionWhenBaseUrlIsNotSandbox() {
        service = new PlatformAdminQueryService(readModel, tenantRepo, propertyRepo, commissionPolicyService, commissionService,
                disbursementRetryService, "https://api.safaricom.co.ke", userRepo, tenantProfileRepo);
        when(tenantRepo.count()).thenReturn(0L);

        AdminOverviewResponse overview = service.getOverview();

        assertThat(overview.environment().environment()).isEqualTo("PRODUCTION");
        assertThat(overview.environment().sandbox()).isFalse();
    }

    @Test
    void landlords_blankSearchUsesFindAll_andMapsAggregates() {
        TenantEntity acme = mockTenant();
        Page<TenantEntity> page = mock(Page.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<TenantEntity, LandlordSummaryResponse>> captor = ArgumentCaptor.forClass(Function.class);
        when(tenantRepo.findAll(any(Pageable.class))).thenReturn(page);
        when(page.map(captor.capture())).thenReturn(Page.empty());

        stubAggregates(acme.getId());
        when(commissionPolicyService.getActiveRate(acme.getId())).thenReturn(new BigDecimal("5.00"));

        service.getLandlords(null, PageRequest.of(0, 20));

        verify(tenantRepo).findAll(any(Pageable.class));
        LandlordSummaryResponse summary = captor.getValue().apply(acme);
        assertThat(summary.name()).isEqualTo("Acme Apartments");
        assertThat(summary.propertiesCount()).isEqualTo(3L);
        assertThat(summary.rentersCount()).isEqualTo(4L);
        assertThat(summary.gmvAmount()).isEqualByComparingTo("1000.00");
        assertThat(summary.commissionAmount()).isEqualByComparingTo("50.00");
        assertThat(summary.effectiveCommissionRate()).isEqualByComparingTo("5.00");
    }

    @Test
    void landlords_searchTermUsesSearchQuery() {
        Page<TenantEntity> page = mock(Page.class);
        when(tenantRepo.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrEmailContainingIgnoreCase(
                eq("acme"), eq("acme"), eq("acme"), any(Pageable.class))).thenReturn(page);
        when(page.map(any())).thenReturn(Page.empty());

        service.getLandlords("acme", PageRequest.of(0, 20));

        verify(tenantRepo).findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrEmailContainingIgnoreCase(
                eq("acme"), eq("acme"), eq("acme"), any(Pageable.class));
    }

    @Test
    void landlordDetail_mapsCollectionsAndCommission() {
        TenantEntity acme = mockTenant();
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.of(acme));

        PropertyJpaEntity prop = mock(PropertyJpaEntity.class);
        when(prop.getId()).thenReturn(propertyId);
        when(prop.getReferenceCode()).thenReturn("PR-1");
        when(prop.getName()).thenReturn("Kipepeo Court");

        UnitJpaEntity unit = mock(UnitJpaEntity.class);
        when(unit.getPropertyId()).thenReturn(propertyId);
        when(unit.getOccupancyStatus()).thenReturn(UnitOccupancyStatus.OCCUPIED);

        LeaseEntity lease = mock(LeaseEntity.class);
        when(lease.getStatus()).thenReturn(LeaseStatus.ACTIVE);

        TenantProfileEntity profile = mock(TenantProfileEntity.class);
        when(profile.getFullName()).thenReturn("Jane Wanjiku");
        when(profile.getEmail()).thenReturn("jane@example.com");

        RentPaymentRequestJpaEntity pr = mock(RentPaymentRequestJpaEntity.class);
        when(pr.getStatus()).thenReturn(RentPaymentRequestStatus.PAID);

        DisbursementJpaEntity disb = mock(DisbursementJpaEntity.class);
        when(disb.getStatus()).thenReturn(DisbursementStatus.SUCCESS);

        RentTransactionJpaEntity txn = mock(RentTransactionJpaEntity.class);
        when(txn.getAmount()).thenReturn(new BigDecimal("20000.00"));
        when(txn.getCommissionAmount()).thenReturn(new BigDecimal("1000.00"));

        when(readModel.findPropertiesByTenant(tenantId)).thenReturn(List.of(prop));
        when(readModel.findUnitsByTenant(tenantId)).thenReturn(List.of(unit));
        when(readModel.findLeasesByTenant(tenantId)).thenReturn(List.of(lease));
        when(readModel.findTenantProfilesByTenant(tenantId)).thenReturn(List.of(profile));
        when(readModel.findPaymentRequestsByTenant(tenantId)).thenReturn(List.of(pr));
        when(readModel.findDisbursementsByTenant(tenantId)).thenReturn(List.of(disb));
        when(readModel.findRecentTransactionsByTenant(eq(tenantId), any(Pageable.class))).thenReturn(List.of(txn));
        when(readModel.sumTransactionAmountsByTenantGrouped())
                .thenReturn(List.of(new TenantIdMoney(tenantId, new BigDecimal("1000.00"), new BigDecimal("50.00"))));
        when(readModel.lastTransactionDateByTenantGrouped())
                .thenReturn(List.of(new TenantIdLastActivity(tenantId, LocalDateTime.now())));
        when(commissionService.getCommissionFor(tenantId))
                .thenReturn(new LandlordCommissionResponse(tenantId, new BigDecimal("5.00"), "OVERRIDE", Instant.now(), Instant.now()));

        LandlordDetailResponse detail = service.getLandlordDetail(tenantId);

        assertThat(detail.name()).isEqualTo("Acme Apartments");
        assertThat(detail.gmvAmount()).isEqualByComparingTo("1000.00");
        assertThat(detail.effectiveCommissionRate()).isEqualByComparingTo("5.00");
        assertThat(detail.commissionSource()).isEqualTo("OVERRIDE");
        assertThat(detail.properties()).hasSize(1);
        assertThat(detail.properties().get(0).unitsCount()).isEqualTo(1L);
        assertThat(detail.properties().get(0).occupiedUnitsCount()).isEqualTo(1L);
        assertThat(detail.renters().get(0).fullName()).isEqualTo("Jane Wanjiku");
        assertThat(detail.recentTransactions()).hasSize(1);
    }

    @Test
    void landlordDetail_unknownLandlord_throwsNotFound() {
        when(tenantRepo.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLandlordDetail(tenantId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------
    // Identity snapshot (userType backfill source of truth)
    // ------------------------------------------------------------------

    private com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity mockUser(
            String clerkUserId,
            com.rentmanager.modules.user.domain.model.UserRole role,
            UUID tenantId) {
        com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity user =
                mock(com.rentmanager.modules.user.infrastructure.persistence.entity.UserEntity.class);
        when(user.getClerkUserId()).thenReturn(clerkUserId);
        when(user.getRole()).thenReturn(role);
        when(user.getTenantId()).thenReturn(tenantId);
        return user;
    }

    @Test
    void getUserTypes_classifiesByLocalDatabaseTruth() {
        var owner = mockUser("user_owner", com.rentmanager.modules.user.domain.model.UserRole.OWNER, tenantId);
        var renter = mockUser("user_renter", null, null);
        var pending = mockUser("user_pending", null, null);
        when(userRepo.findAll()).thenReturn(List.of(owner, renter, pending));
        when(tenantProfileRepo.findAllClerkUserIds())
                .thenReturn(List.of("user_renter"));

        var snapshots = service.getUserTypes();

        assertThat(snapshots).extracting("clerkUserId")
                .containsExactlyInAnyOrder("user_owner", "user_renter", "user_pending");
        assertThat(snapshots).extracting("userType")
                .containsExactlyInAnyOrder("landlord", "renter", "landlord_pending");
    }

    @Test
    void getUserTypes_tenantBoundWithoutRole_isLandlord() {
        var bound = mockUser("user_bound", null, tenantId);
        when(userRepo.findAll()).thenReturn(List.of(bound));
        when(tenantProfileRepo.findAllClerkUserIds()).thenReturn(List.of());

        var snapshots = service.getUserTypes();

        assertThat(snapshots).extracting("userType").containsExactly("landlord");
    }

    @Test
    void getUserTypes_renterWithoutUserRow_isStillRenter() {
        var renter = mockUser("user_renter", null, null);
        when(userRepo.findAll()).thenReturn(List.of(renter));
        when(tenantProfileRepo.findAllClerkUserIds())
                .thenReturn(List.of("user_renter", "user_other_renter_only"));

        var snapshots = service.getUserTypes();

        assertThat(snapshots).extracting("clerkUserId")
                .containsExactlyInAnyOrder("user_renter", "user_other_renter_only");
        assertThat(snapshots).extracting("userType")
                .containsExactlyInAnyOrder("renter", "renter");
    }

    private void stubAggregates(UUID id) {
        when(readModel.countPropertiesByTenantGrouped())
                .thenReturn(List.of(new TenantIdCount(id, 3L)));
        when(readModel.countUnitsByTenantGrouped())
                .thenReturn(List.of(new TenantIdCount(id, 2L)));
        when(readModel.countLeasesByTenantGroupedByStatus(LeaseStatus.ACTIVE))
                .thenReturn(List.of(new TenantIdCount(id, 1L)));
        when(readModel.countRentersByTenantGrouped())
                .thenReturn(List.of(new TenantIdCount(id, 4L)));
        when(readModel.sumTransactionAmountsByTenantGrouped())
                .thenReturn(List.of(new TenantIdMoney(id, new BigDecimal("1000.00"), new BigDecimal("50.00"))));
        when(readModel.lastTransactionDateByTenantGrouped())
                .thenReturn(List.of(new TenantIdLastActivity(id, LocalDateTime.now())));
    }

    private TenantEntity mockTenant() {
        TenantEntity t = mock(TenantEntity.class);
        when(t.getId()).thenReturn(tenantId);
        when(t.getName()).thenReturn("Acme Apartments");
        when(t.getSlug()).thenReturn("acme");
        when(t.getEmail()).thenReturn("acme@example.com");
        when(t.getBillingMode()).thenReturn(BillingMode.COMMISSION);
        when(t.getStatus()).thenReturn(TenantStatus.ACTIVE);
        when(t.getCreatedAt()).thenReturn(Instant.now());
        return t;
    }
}
