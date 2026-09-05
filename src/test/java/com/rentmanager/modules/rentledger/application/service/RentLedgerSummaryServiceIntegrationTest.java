package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.application.dto.RentLedgerSummary;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The landlord dashboard's financial figures, verified against real SQL.
 *
 * <p>These replace six cards built from a hard-coded array. A mocked test
 * would prove the service calls the repository; only a database can prove the
 * aggregates actually sum what they claim to — which is the entire reason
 * this endpoint exists.
 */
@Transactional
class RentLedgerSummaryServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final ZoneId TZ = ZoneId.of("Africa/Nairobi");

    @Autowired
    private RentLedgerSummaryService summaryService;

    @Autowired
    private RentLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private RentLedgerApplicationService rentLedgerApplicationService;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID leaseId;
    private UUID unitId;
    private UUID tenantProfileId;

    @BeforeEach
    void setUp() {
        MinimalTenantChainFixture.ChainWithLease chain =
                MinimalTenantChainFixture.persistFullChainWithLease(entityManager);
        tenantId = chain.tenantId();
        leaseId = chain.leaseId();
        unitId = chain.unitId();
        tenantProfileId = chain.tenantProfileId();
        entityManager.flush();
    }

    /** Creates a charge in a given month and returns its id. */
    private UUID charge(LocalDate periodStart, String amountDue) {
        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId,
                "summary-it-" + UUID.randomUUID(),
                leaseId,
                unitId,
                tenantProfileId,
                periodStart,
                periodStart.plusMonths(1).minusDays(1),
                periodStart,
                new BigDecimal(amountDue),
                false
        );
        UUID id = ledgerEntryRepository.save(entry).getId();
        entityManager.flush();
        return id;
    }

    private void pay(UUID entryId, String amount) {
        rentLedgerApplicationService.applyTransaction(
                tenantId,
                "summary-pay-" + UUID.randomUUID(),
                entryId,
                RentTransactionType.PAYMENT,
                new BigDecimal(amount),
                "RCPT-" + UUID.randomUUID(),
                RentTransactionSource.MPESA,
                "SYSTEM",
                LocalDateTime.now()
        );
        entityManager.flush();
    }

    private LocalDate thisMonth() {
        return LocalDate.now(TZ).withDayOfMonth(1);
    }

    @Test
    void aLandlordWithNoEntriesGetsGenuineZeroesNotNulls() {
        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("0");
        assertThat(summary.outstandingTotal()).isEqualByComparingTo("0");
        assertThat(summary.overdueTotal()).isEqualByComparingTo("0");
        assertThat(summary.overdueEntryCount()).isZero();
        assertThat(summary.currency()).isNotBlank();
    }

    @Test
    void collectedThisMonthSumsWhatActuallyArrived() {
        UUID entry = charge(thisMonth(), "20000.00");
        pay(entry, "12000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("12000.00");
    }

    /**
     * The distinction the old cards blurred: what was invoiced is not what was
     * collected. A landlord reading "collected" must not be shown the charge.
     */
    @Test
    void collectedIsNotTheAmountCharged() {
        UUID entry = charge(thisMonth(), "50000.00");
        pay(entry, "5000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("5000.00");
        assertThat(summary.collectedThisMonth()).isNotEqualByComparingTo("50000.00");
    }

    @Test
    void lastMonthsCollectionDoesNotCountTowardThisMonth() {
        UUID lastMonth = charge(thisMonth().minusMonths(1), "30000.00");
        pay(lastMonth, "30000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("0");
    }

    /**
     * Arrears do not expire. A figure filtered to the current month would
     * understate what the landlord is actually chasing.
     */
    @Test
    void outstandingIncludesArrearsFromEarlierMonths() {
        charge(thisMonth().minusMonths(3), "15000.00");
        charge(thisMonth(), "15000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.outstandingTotal()).isEqualByComparingTo("30000.00");
    }

    @Test
    void outstandingIsNetOfPartialPayments() {
        UUID entry = charge(thisMonth(), "20000.00");
        pay(entry, "8000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.outstandingTotal()).isEqualByComparingTo("12000.00");
    }

    @Test
    void aFullySettledChargeLeavesNothingOutstanding() {
        UUID entry = charge(thisMonth(), "20000.00");
        pay(entry, "20000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.outstandingTotal()).isEqualByComparingTo("0");
        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("20000.00");
    }

    /**
     * OVERDUE is set by the nightly sweep, not by this query. An unpaid charge
     * that has not yet been swept is outstanding but not overdue, and
     * reporting it as overdue would have the dashboard accuse a tenant the
     * ledger has not.
     */
    @Test
    void anUnsweptUnpaidChargeIsOutstandingButNotYetOverdue() {
        charge(thisMonth(), "20000.00");
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.outstandingTotal()).isEqualByComparingTo("20000.00");
        assertThat(summary.overdueTotal()).isEqualByComparingTo("0");
        assertThat(summary.overdueEntryCount()).isZero();
    }

    /**
     * Tenant scoping, asserted rather than assumed. Every figure on this
     * dashboard is portfolio-wide, so a leak here would show one landlord
     * another's revenue.
     */
    @Test
    void oneLandlordsFiguresNeverIncludeAnothers() {
        UUID mine = charge(thisMonth(), "20000.00");
        pay(mine, "20000.00");

        MinimalTenantChainFixture.ChainWithLease other =
                MinimalTenantChainFixture.persistFullChainWithLease(entityManager);
        RentLedgerEntry theirs = RentLedgerEntry.create(
                other.tenantId(), "other-" + UUID.randomUUID(), other.leaseId(),
                other.unitId(), other.tenantProfileId(),
                thisMonth(), thisMonth().plusMonths(1).minusDays(1), thisMonth(),
                new BigDecimal("999999.00"), false);
        ledgerEntryRepository.save(theirs);
        entityManager.flush();
        entityManager.clear();

        RentLedgerSummary summary = summaryService.getSummary(tenantId);

        assertThat(summary.collectedThisMonth()).isEqualByComparingTo("20000.00");
        assertThat(summary.outstandingTotal())
                .as("the other landlord's 999,999 must not appear here")
                .isEqualByComparingTo("0");
    }
}
