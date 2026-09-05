package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test proving B2C callback idempotency at the DB level.
 *
 * The existing {@code B2CCallbackControllerIdempotencyTest} is a
 * {@code @WebMvcTest} that mocks B2CDisbursementService entirely — it proves
 * the controller always returns 200 and calls the service twice, but cannot
 * verify that duplicate callbacks do not create duplicate REFUND
 * {@code RentTransaction} rows in Postgres.
 *
 * This test fills that gap: it wires the real B2CDisbursementService (with
 * only DarajaB2CService mocked to avoid external HTTP calls) against a real
 * Postgres container and proves:
 *   1. Duplicate success callbacks produce exactly one REFUND transaction.
 *   2. A timeout callback after a successful callback is safely ignored.
 *   3. A failure callback creates no REFUND transaction at all.
 */
class B2CCallbackIdempotencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @MockBean
    private DarajaB2CService darajaB2CService;

    @Autowired
    private B2CDisbursementService b2cDisbursementService;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;

    @Autowired
    private RentTransactionRepository rentTransactionRepository;

    @Autowired
    private RentLedgerApplicationService rentLedgerApplicationService;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID leaseId;
    private UUID unitId;
    private UUID tenantProfileId;
    private UUID ledgerEntryId;

    @BeforeEach
    void setUp() {
        when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                .thenReturn("OCID-IT-" + UUID.randomUUID());

        MinimalTenantChainFixture.ChainWithLease chain = MinimalTenantChainFixture.persistFullChainWithLease(entityManager);
        tenantId = chain.tenantId();
        unitId = chain.unitId();
        tenantProfileId = chain.tenantProfileId();
        leaseId = chain.leaseId();

        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId, "corr-" + UUID.randomUUID(), leaseId, unitId, tenantProfileId,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), LocalDate.of(2026, 7, 1),
                new BigDecimal("1000.00"), false
        );
        entry = rentLedgerEntryRepository.save(entry);
        entityManager.flush();
        ledgerEntryId = entry.getId();

        // Preconditions the initiation guards now require. Both are real
        // product state, not test scaffolding: a landlord cannot be paid
        // without a registered payout number, and a payout cannot exceed the
        // proceeds the charge has actually collected.
        entityManager.createNativeQuery(
                        "UPDATE tenants SET payout_phone_number = :phone WHERE id = :id")
                .setParameter("phone", "+254711000111")
                .setParameter("id", tenantId)
                .executeUpdate();

        rentLedgerApplicationService.applyTransaction(
                tenantId,
                "it-payment-" + UUID.randomUUID(),
                ledgerEntryId,
                RentTransactionType.PAYMENT,
                new BigDecimal("10000.00"),
                "IT-RECEIPT-" + UUID.randomUUID(),
                RentTransactionSource.MPESA,
                "SYSTEM",
                LocalDateTime.now()
        );
        entityManager.flush();
    }

    /**
     * Proves that delivering the same success callback twice creates exactly
     * one REFUND transaction. The first call transitions the disbursement to
     * SUCCESS and posts a REFUND; the second call hits the terminal-status
     * guard in {@link B2CDisbursementService#handleResult} and returns
     * immediately without posting a duplicate.
     */
    @Test
    @Transactional
    void duplicateSuccessCallback_doesNotCreateDuplicateRefund() {
        Disbursement disbursement = b2cDisbursementService.initiateDisbursement(
                tenantId, leaseId, ledgerEntryId,
                new BigDecimal("5000.00"), "BusinessPayment", "Test disbursement"
        );
        entityManager.flush();
        assertThat(disbursement.getStatus()).isEqualTo(DisbursementStatus.PENDING);

        b2cDisbursementService.handleResult(
                disbursement.getId(), "0", "Success", "TXN-IT-001", "CONV-IT-001"
        );
        entityManager.flush();
        entityManager.clear();

        // Filtered by type rather than counting every row on the entry. The
        // setup now posts a real PAYMENT so the charge has proceeds to
        // disburse — without one the initiation guard correctly refuses — so
        // a bare size check would be asserting the absence of the very
        // precondition this test needs.
        assertThat(refundsOnEntry())
                .as("first callback creates exactly one REFUND")
                .hasSize(1);

        b2cDisbursementService.handleResult(
                disbursement.getId(), "0", "Success", "TXN-IT-001", "CONV-IT-001"
        );
        entityManager.flush();

        assertThat(refundsOnEntry())
                .as("duplicate callback does not create a second REFUND")
                .hasSize(1);
    }

    /**
     * Proves that a timeout callback delivered after a successful result is
     * safely ignored — the disbursement stays SUCCESS and the failure reason
     * is not overwritten.
     */
    @Test
    @Transactional
    void successThenTimeout_doesNotOverwriteStatus() {
        Disbursement disbursement = b2cDisbursementService.initiateDisbursement(
                tenantId, leaseId, ledgerEntryId,
                new BigDecimal("3000.00"), "SalaryPayment", "Test disbursement 2"
        );
        entityManager.flush();

        b2cDisbursementService.handleResult(
                disbursement.getId(), "0", "Success", "TXN-IT-002", "CONV-IT-002"
        );
        entityManager.flush();

        b2cDisbursementService.handleTimeout(disbursement.getId());
        entityManager.flush();
        entityManager.clear();

        Disbursement reloaded = disbursementRepository.findById(disbursement.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DisbursementStatus.SUCCESS);
        assertThat(reloaded.getFailureReason()).isNull();
    }

    /**
     * Proves that a failure callback (non-zero ResultCode) transitions the
     * disbursement to FAILED and does NOT create a REFUND transaction.
     */
    @Test
    @Transactional
    void failureCallback_doesNotCreateRefund() {
        Disbursement disbursement = b2cDisbursementService.initiateDisbursement(
                tenantId, leaseId, ledgerEntryId,
                new BigDecimal("2000.00"), "BusinessPayment", "Test disbursement 3"
        );
        entityManager.flush();

        b2cDisbursementService.handleResult(
                disbursement.getId(), "1", "Insufficient funds", null, "CONV-IT-003"
        );
        entityManager.flush();
        entityManager.clear();

        Disbursement reloaded = disbursementRepository.findById(disbursement.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(DisbursementStatus.FAILED);
        assertThat(reloaded.getFailureReason()).isEqualTo("Insufficient funds");

        assertThat(refundsOnEntry())
                .as("failure callback creates no REFUND transaction")
                .isEmpty();
    }

    private java.util.List<com.rentmanager.modules.rentledger.domain.model.RentTransaction> refundsOnEntry() {
        return rentTransactionRepository.findByLedgerEntry(tenantId, ledgerEntryId).stream()
                .filter(t -> t.getType() == RentTransactionType.REFUND)
                .toList();
    }
}
