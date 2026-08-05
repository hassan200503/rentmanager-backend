package com.rentmanager.modules.platformadmin.infrastructure.persistence;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.lease.infrastructure.persistence.repository.JpaLeaseRepository;
import com.rentmanager.modules.platformadmin.api.dto.response.AdminOverviewResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordDetailResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordSummaryResponse;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminQueryService;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.projection.TenantStatusCount;
import com.rentmanager.modules.platformadmin.infrastructure.persistence.repository.AdminReadModelRepository;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentPaymentRequestJpaRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentLedgerEntryJpaRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentTransactionJpaRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the platform-admin read model against real Postgres: the
 * constructor-expression aggregations and per-tenant collections in
 * {@code AdminReadModelRepository} and the mapping in
 * {@code PlatformAdminQueryService}. Sits on {@code AbstractPostgresIntegrationTest}
 * (shared Testcontainers Postgres), so all overview assertions are tolerant
 * (>= seeded values) while the per-tenant/detail assertions are exact, since
 * they are scoped to this test's uniquely-suffixed landlord slug.
 */
class PlatformAdminReadModelIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String SLUG = "acme-admin-int-" + UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private PlatformAdminQueryService queryService;

    @Autowired
    private AdminReadModelRepository readModel;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PropertyJpaRepository propertyJpaRepository;

    @Autowired
    private UnitJpaRepository unitJpaRepository;

    @Autowired
    private JpaLeaseRepository leaseJpaRepository;

    @Autowired
    private TenantProfileJpaRepository tenantProfileJpaRepository;

    @Autowired
    private RentTransactionJpaRepository rentTransactionJpaRepository;

    @Autowired
    private RentPaymentRequestJpaRepository rentPaymentRequestJpaRepository;
    @Autowired
    private RentLedgerEntryJpaRepository rentLedgerEntryJpaRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private CommissionPolicyRepository commissionPolicyRepository;

    @Test
    @Transactional
    void readModel_aggregatesOverviewListAndDetail() {
Tenant acme = Tenant.create("ADM-TEST-" + UUID.randomUUID(), "Acme Apartments", SLUG, "acme@example.com",
                "+254700000001", TenantType.STANDARD);
        acme.activate();
        Tenant persistedAcme = tenantRepository.save(acme);
        UUID tenantId = persistedAcme.getId();

        Tenant beta = Tenant.create("ADM-B-" + UUID.randomUUID(), "Beta Court", "beta-" + SLUG, "beta@example.com",
                "+254700000002", TenantType.STANDARD);
        beta.suspend();
        tenantRepository.save(beta);

        PropertyJpaEntity property = PropertyJpaEntity.create(tenantId);
        property.setReferenceCode("ADM-PR-1");
        property.setName("Acme Block A");
        property.setStatus(PropertyStatus.ACTIVE);
        property.setPropertyType(PropertyType.APARTMENT);
        property.setPremisesType(PremisesType.RESIDENTIAL);
        property.setOccupancyStatus(OccupancyStatus.FULLY_OCCUPIED);
        propertyJpaRepository.save(property);

        UnitJpaEntity unit = UnitJpaEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .propertyId(property.getId())
                .unitNumber("A1")
                .status(UnitStatus.ACTIVE)
                .occupancyStatus(UnitOccupancyStatus.OCCUPIED)
                .rentAmount(new BigDecimal("20000.00"))
                .build();
        unitJpaRepository.save(unit);

        TenantProfileEntity profile = new TenantProfileEntity(
                UUID.randomUUID(), tenantId, "clerk-" + SLUG, "Jane Wanjiku", "jane@example.com",
                "+254711111111", "12345678", false);
        tenantProfileJpaRepository.save(profile);

        LeaseEntity lease = new LeaseEntity();
        lease.assignTenant(tenantId);
        lease.setLeaseNumber("ADM-L-" + SLUG);
        lease.setPropertyId(property.getId());
        lease.setUnitId(unit.getId());
        lease.setTenantProfileId(profile.getId());
        lease.setStartDate(LocalDate.now());
        lease.setEndDate(LocalDate.now().plusYears(1));
        lease.setRentAmount(new BigDecimal("20000.00"));
        lease.setDepositAmount(new BigDecimal("20000.00"));
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setLeaseType(LeaseType.FIXED_TERM);
        lease.setBillingCycle(BillingCycle.MONTHLY);
        leaseJpaRepository.save(lease);

        UUID ledgerEntryId = UUID.randomUUID();
        RentLedgerEntryJpaEntity entry = new RentLedgerEntryJpaEntity();
        entry.restoreId(ledgerEntryId);
        entry.assignTenant(tenantId);
        entry.setLeaseId(lease.getId());
        entry.setUnitId(unit.getId());
        entry.setTenantProfileId(profile.getId());
        entry.setBillingPeriodStart(LocalDate.now());
        entry.setBillingPeriodEnd(LocalDate.now().plusDays(30));
        entry.setDueDate(LocalDate.now());
        entry.setAmountDue(new BigDecimal("20000.00"));
        entry.setAmountPaid(new BigDecimal("20000.00"));
        entry.setStatus(RentLedgerStatus.PAID);
        entry.setProrated(false);
        rentLedgerEntryJpaRepository.save(entry);

        RentTransactionJpaEntity txn = new RentTransactionJpaEntity();
        txn.assignTenant(tenantId);
        txn.setLedgerEntryId(ledgerEntryId);
        txn.setLeaseId(lease.getId());
        txn.setType(RentTransactionType.PAYMENT);
        txn.setAmount(new BigDecimal("20000.00"));
        txn.setSource(RentTransactionSource.MPESA);
        txn.setRecordedBy("SYSTEM");
        txn.setOccurredAt(LocalDateTime.now());
        txn.setCommissionRatePercent(new BigDecimal("3.00"));
        txn.setCommissionAmount(new BigDecimal("600.00"));
        txn.setNetAmount(new BigDecimal("19400.00"));
        rentTransactionJpaRepository.save(txn);

        RentPaymentRequestJpaEntity pr = new RentPaymentRequestJpaEntity();
        pr.assignTenant(tenantId);
        pr.setLeaseId(lease.getId());
        pr.setRentLedgerEntryId(ledgerEntryId);
        pr.setAmount(new BigDecimal("20000.00"));
        pr.setStatus(RentPaymentRequestStatus.PAID);
        pr.setMpesaReceiptNumber("ADM-RCPT-" + SLUG);
        rentPaymentRequestJpaRepository.save(pr);

        disbursementRepository.save(Disbursement.create(
                tenantId, lease.getId(), UUID.randomUUID(),
                new BigDecimal("19400.00"), "+254700000001", "Acme", "BusinessPayment"));

        commissionPolicyRepository.save(CommissionPolicy.create(new BigDecimal("5.00"), Instant.now(), "seed"));
        commissionPolicyRepository.save(
                CommissionPolicy.createForLandlord(new BigDecimal("3.00"), Instant.now(), "seed", tenantId));

        // ----- overview (tolerant to other tests sharing the container) -----
        AdminOverviewResponse overview = queryService.getOverview();
        assertThat(overview.platform().totalTenants()).isGreaterThanOrEqualTo(2L);
        assertThat(overview.platform().activeTenants()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.platform().suspendedTenants()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.platform().totalProperties()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.platform().totalUnits()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.platform().activeLeases()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.platform().totalRenters()).isGreaterThanOrEqualTo(1L);
        assertThat(overview.payments().gmvCurrentMonth().doubleValue()).isGreaterThanOrEqualTo(20000.0);
        assertThat(overview.payments().commissionCurrentMonth().doubleValue()).isGreaterThanOrEqualTo(600.0);
        assertThat(overview.environment().environment()).isNotNull();

        Map<TenantStatus, Long> statusCounts = readModel.countTenantsByStatus().stream()
                .collect(Collectors.toMap(TenantStatusCount::status, TenantStatusCount::count));
        assertThat(statusCounts.getOrDefault(TenantStatus.ACTIVE, 0L)).isGreaterThanOrEqualTo(1L);
        assertThat(statusCounts.getOrDefault(TenantStatus.SUSPENDED, 0L)).isGreaterThanOrEqualTo(1L);

        // ----- landlord list, searched by unique slug (exact) -----
        List<LandlordSummaryResponse> rows = queryService.getLandlords(SLUG, PageRequest.of(0, 50)).getContent();
        LandlordSummaryResponse row = rows.stream()
                .filter(r -> SLUG.equals(r.slug()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected landlord with slug " + SLUG + " in search results, got: " + rows));
        assertThat(row.name()).isEqualTo("Acme Apartments");
        assertThat(row.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(row.propertiesCount()).isEqualTo(1L);
        assertThat(row.unitsCount()).isEqualTo(1L);
        assertThat(row.activeLeasesCount()).isEqualTo(1L);
        assertThat(row.rentersCount()).isEqualTo(1L);
        assertThat(row.gmvAmount()).isEqualByComparingTo("20000.00");
        assertThat(row.commissionAmount()).isEqualByComparingTo("600.00");
        assertThat(row.effectiveCommissionRate()).isEqualByComparingTo("3.00");

        // ----- landlord detail (exact) -----
        LandlordDetailResponse detail = queryService.getLandlordDetail(tenantId);
        assertThat(detail.name()).isEqualTo("Acme Apartments");
        assertThat(detail.effectiveCommissionRate()).isEqualByComparingTo("3.00");
        assertThat(detail.commissionSource()).isEqualTo("OVERRIDE");
        assertThat(detail.gmvAmount()).isEqualByComparingTo("20000.00");
        assertThat(detail.properties()).hasSize(1);
        assertThat(detail.properties().get(0).unitsCount()).isEqualTo(1L);
        assertThat(detail.properties().get(0).occupiedUnitsCount()).isEqualTo(1L);
        assertThat(detail.renters()).hasSize(1);
        assertThat(detail.renters().get(0).fullName()).isEqualTo("Jane Wanjiku");
        assertThat(detail.paymentRequests()).hasSize(1);
        assertThat(detail.paymentRequests().get(0).status()).isEqualTo(RentPaymentRequestStatus.PAID);
        assertThat(detail.disbursements()).hasSize(1);
        assertThat(detail.disbursements().get(0).status()).isEqualTo(DisbursementStatus.INITIATED);
        assertThat(detail.recentTransactions()).hasSize(1);
        assertThat(detail.recentTransactions().get(0).commissionAmount()).isEqualByComparingTo("600.00");
    }
}
