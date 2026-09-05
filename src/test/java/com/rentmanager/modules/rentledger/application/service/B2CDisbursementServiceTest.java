package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaException;
import com.rentmanager.shared.observability.BusinessMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@code B2CDisbursementService}.
 *
 * Covers:
 *   1. Pre-persist ordering: INITIATED row is saved BEFORE calling Daraja.
 *      A crash between Daraja response and status update leaves a traceable
 *      INITIATED (or FAILED) row — never a silent loss.
 *   2. Duplicate callback guard: handleResult() called twice with the same
 *      success payload is a no-op on the second call.
 *   3. Duplicate timeout callback: handleTimeout() on a non-PENDING
 *      disbursement is a no-op.
 *   4. Full success path: INITIATED → PENDING → SUCCESS, REFUND transaction
 *      posted to rent ledger.
 *   5. Failure path: Daraja API failure → INITIATED saved → FAILED saved
 *      before exception rethrown.
 *   6. Non-zero result code → FAILED, no ledger transaction posted.
 */
class B2CDisbursementServiceTest {

    private DisbursementRepository disbursementRepository;
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    private RentLedgerApplicationService rentLedgerApplicationService;
    private DarajaB2CService darajaB2CService;
    private TenantRepository tenantRepository;
    private DisbursementEntitlementService entitlementService;

    private B2CDisbursementService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("5000.00");
    private static final String PHONE = "+254712345678";
    private static final String NAME = "Test Recipient";
    private static final String COMMAND_ID = "BusinessPayment";
    private static final String REMARKS = "Test disbursement";
    private static final String OCID = "ocid-" + UUID.randomUUID();
    private static final String TXN_ID = "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private static final String CONV_ID = "conv-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        disbursementRepository = mock(DisbursementRepository.class);
        rentLedgerEntryRepository = mock(RentLedgerEntryRepository.class);
        rentLedgerApplicationService = mock(RentLedgerApplicationService.class);
        darajaB2CService = mock(DarajaB2CService.class);

        tenantRepository = mock(TenantRepository.class);
        entitlementService = mock(DisbursementEntitlementService.class);

        // The landlord's registered payout number is now the only possible
        // destination — the caller no longer supplies one.
        Tenant landlord = mock(Tenant.class);
        when(landlord.getPayoutPhoneNumber()).thenReturn(PHONE);
        when(landlord.getName()).thenReturn(NAME);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(landlord));
        when(entitlementService.settleableAmount(TENANT_ID, ENTRY_ID))
                .thenReturn(new BigDecimal("1000000"));

        // The charge is read under a PESSIMISTIC_WRITE lock before entitlement
        // is computed, so this has to resolve for initiation to proceed.
        RentLedgerEntry lockedEntry = mock(RentLedgerEntry.class);
        when(rentLedgerEntryRepository.findByIdAndTenantIdForUpdate(any(), any()))
                .thenReturn(Optional.of(lockedEntry));

        // A real transaction service so initiation runs the same two-bean
        // path as production, lock read included.
        DisbursementTransactionService transactionService = new DisbursementTransactionService(
                disbursementRepository,
                rentLedgerEntryRepository,
                tenantRepository,
                entitlementService,
                mock(FinancialAuditService.class),
                mock(BusinessMetrics.class)
        );

        service = new B2CDisbursementService(
                disbursementRepository,
                rentLedgerEntryRepository,
                rentLedgerApplicationService,
                darajaB2CService,
                mock(FinancialAuditService.class),
                mock(BusinessMetrics.class),
                transactionService
        );

        // By default, save() returns its argument unchanged
        when(disbursementRepository.save(any(Disbursement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pre-persist ordering
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class PrePersistOrdering {

        /**
         * This is the core safety property from the architecture:
         * save(INITIATED) must happen BEFORE any Daraja API call.
         * If Daraja succeeds but the app crashes before saving PENDING,
         * the INITIATED row is the audit trail.
         *
         * We track the status at the moment of each save() call using a
         * mutable capture — the service reuses the same Disbursement object,
         * so we must record its status at save time, not after.
         */
        @Test
        void savesInitiatedRecord_beforeCallingDaraja() {
            when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                    .thenReturn(OCID);

            // Track the status at the time each save() is called
            List<DisbursementStatus> savedStatuses = new ArrayList<>();
            when(disbursementRepository.save(any(Disbursement.class)))
                    .thenAnswer(inv -> {
                        Disbursement d = inv.getArgument(0);
                        savedStatuses.add(d.getStatus());
                        return d;
                    });

            service.initiateDisbursement(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT,
                    COMMAND_ID, REMARKS);

            InOrder inOrder = inOrder(disbursementRepository, darajaB2CService);

            // Verify order: first save, then Daraja, then second save
            inOrder.verify(disbursementRepository).save(any(Disbursement.class));
            inOrder.verify(darajaB2CService).initiateB2C(
                    eq(AMOUNT), eq(PHONE), eq(NAME), eq(REMARKS), eq(COMMAND_ID));
            inOrder.verify(disbursementRepository).save(any(Disbursement.class));

            // First save must have been INITIATED
            assertEquals(DisbursementStatus.INITIATED, savedStatuses.get(0),
                    "First save must be INITIATED (before Daraja call)");
            // Second save must have been PENDING
            assertEquals(DisbursementStatus.PENDING, savedStatuses.get(1),
                    "Second save must be PENDING (after successful Daraja call)");
        }

        /**
         * If Daraja throws (network error, auth error, etc.), the service must:
         *   1. Save the disbursement as INITIATED (already done before the call)
         *   2. Update it to FAILED (preserving the audit trail)
         *   3. Save the FAILED state
         *   4. Rethrow
         *
         * A record stuck in INITIATED (if step 2/3 is skipped) would be an
         * invisible "lost" disbursement. The FAILED state here marks the
         * initiation attempt — it's not a Daraja result callback.
         */
        @Test
        void daraja_failure_savesFailedRecord_beforeRethrowing() {
            when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                    .thenThrow(new DarajaException("B2C request failed"));

            // Track the status at the time each save() is called
            List<DisbursementStatus> savedStatuses = new ArrayList<>();
            when(disbursementRepository.save(any(Disbursement.class)))
                    .thenAnswer(inv -> {
                        Disbursement d = inv.getArgument(0);
                        savedStatuses.add(d.getStatus());
                        return d;
                    });

            assertThrows(RentLedgerStateException.class, () ->
                    service.initiateDisbursement(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT,
                            COMMAND_ID, REMARKS));

            // Two saves: INITIATED first, then FAILED
            assertEquals(2, savedStatuses.size());
            assertEquals(DisbursementStatus.INITIATED, savedStatuses.get(0));
            assertEquals(DisbursementStatus.FAILED, savedStatuses.get(1));
        }

        @Test
        void successfulInitiation_returnsDisbursementWithPendingStatus() {
            when(darajaB2CService.initiateB2C(any(), any(), any(), any(), any()))
                    .thenReturn(OCID);

            Disbursement result = service.initiateDisbursement(TENANT_ID, LEASE_ID, ENTRY_ID,
                    AMOUNT, COMMAND_ID, REMARKS);

            assertEquals(DisbursementStatus.PENDING, result.getStatus());
            assertEquals(OCID, result.getMpesaOriginatorConversationId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Duplicate callback guard (idempotency)
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class IdempotentCallbacks {

        /**
         * This is the exact scenario Daraja's documentation warns about:
         * the same result callback can be delivered more than once. The
         * second delivery must be a no-op — no duplicate REFUND transaction.
         */
        @Test
        void handleResult_secondSuccessCallback_isNoOp_noDoubleRefund() {
            Disbursement d = buildPendingDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            // First callback: SUCCESS
            service.handleResult(disbursementId, "0", "Success", TXN_ID, CONV_ID);

            // Simulate that the disbursement is now SUCCESS (as saved)
            // The save() mock returns the same object, so d.getStatus() is now SUCCESS
            assertEquals(DisbursementStatus.SUCCESS, d.getStatus(),
                    "Precondition: disbursement must be SUCCESS after first callback");

            // Second callback: duplicate SUCCESS delivery
            service.handleResult(disbursementId, "0", "Success", TXN_ID, CONV_ID);

            // applyTransaction must be called only ONCE — not twice
            verify(rentLedgerApplicationService, times(1)).applyTransaction(
                    any(), any(), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void handleResult_secondFailureCallback_isNoOp() {
            Disbursement d = buildPendingDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            // First callback: FAILED
            service.handleResult(disbursementId, "1", "Insufficient funds", null, CONV_ID);
            assertEquals(DisbursementStatus.FAILED, d.getStatus());

            // Second callback: same failure — must be ignored
            service.handleResult(disbursementId, "1", "Insufficient funds", null, CONV_ID);

            // Repository save called for the first callback; second call returns early
            // before save — so total saves = 1 (FAILED state from first callback)
            verify(disbursementRepository, times(1)).save(any(Disbursement.class));
        }

        @Test
        void handleResult_onAlreadySuccessfulDisbursement_returnsExistingRecord() {
            Disbursement d = buildSuccessDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            Disbursement result = service.handleResult(
                    disbursementId, "0", "Success", TXN_ID, CONV_ID);

            // Returns the existing disbursement unchanged
            assertEquals(DisbursementStatus.SUCCESS, result.getStatus());
            // No ledger transaction posted (this was already done on first callback)
            verifyNoInteractions(rentLedgerApplicationService);
            // No save (nothing changed)
            verify(disbursementRepository, never()).save(any());
            verify(disbursementRepository).findById(disbursementId);
        }

        @Test
        void handleTimeout_onNonPendingDisbursement_isNoOp() {
            Disbursement d = buildSuccessDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            Disbursement result = service.handleTimeout(disbursementId);

            assertEquals(DisbursementStatus.SUCCESS, result.getStatus());
            // No state change, no save
            verify(disbursementRepository, never()).save(any());
        }

        @Test
        void handleTimeout_onPendingDisbursement_setsFailedStatus() {
            Disbursement d = buildPendingDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            Disbursement result = service.handleTimeout(disbursementId);

            assertEquals(DisbursementStatus.FAILED, result.getStatus());
            assertNotNull(result.getFailureReason());
            verify(disbursementRepository).save(d);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Full success path
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class SuccessPath {

        @Test
        void handleResult_resultCodeZero_postsRefundTransactionToLedger() {
            Disbursement d = buildPendingDisbursement();
            UUID disbursementId = d.getId();

            when(disbursementRepository.findById(disbursementId)).thenReturn(Optional.of(d));

            service.handleResult(disbursementId, "0", "Success", TXN_ID, CONV_ID);

            // Verify the REFUND transaction was posted to the ledger
            verify(rentLedgerApplicationService).applyTransaction(
                    eq(TENANT_ID),
                    eq("disbursement-" + disbursementId),
                    eq(ENTRY_ID),
                    eq(RentTransactionType.REFUND),
                    eq(AMOUNT),
                    eq(TXN_ID),
                    eq(RentTransactionSource.MPESA),
                    eq("SYSTEM"),
                    any() // LocalDateTime.now()
            );
        }

        @Test
        void handleResult_resultCodeZero_setsDisbursementStatusToSuccess() {
            Disbursement d = buildPendingDisbursement();
            when(disbursementRepository.findById(d.getId())).thenReturn(Optional.of(d));

            service.handleResult(d.getId(), "0", "Success", TXN_ID, CONV_ID);

            assertEquals(DisbursementStatus.SUCCESS, d.getStatus());
            assertEquals(TXN_ID, d.getMpesaTransactionId());
            assertEquals(CONV_ID, d.getMpesaConversationId());
        }

        @Test
        void handleResult_resultCodeZero_withNullLedgerEntryId_doesNotPostLedgerTransaction() {
            // A disbursement without a ledger entry (e.g. ad-hoc payout) should
            // not attempt to post a ledger transaction.
            Disbursement d = buildPendingDisbursementWithNullEntry();
            when(disbursementRepository.findById(d.getId())).thenReturn(Optional.of(d));

            service.handleResult(d.getId(), "0", "Success", TXN_ID, CONV_ID);

            assertEquals(DisbursementStatus.SUCCESS, d.getStatus());
            verifyNoInteractions(rentLedgerApplicationService);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Failure path
    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class FailurePath {

        @Test
        void handleResult_nonZeroResultCode_setsDisbursementStatusToFailed() {
            Disbursement d = buildPendingDisbursement();
            when(disbursementRepository.findById(d.getId())).thenReturn(Optional.of(d));

            service.handleResult(d.getId(), "1", "Insufficient funds", null, CONV_ID);

            assertEquals(DisbursementStatus.FAILED, d.getStatus());
            assertEquals("Insufficient funds", d.getFailureReason());
        }

        @Test
        void handleResult_nonZeroResultCode_doesNotPostLedgerTransaction() {
            Disbursement d = buildPendingDisbursement();
            when(disbursementRepository.findById(d.getId())).thenReturn(Optional.of(d));

            service.handleResult(d.getId(), "2001", "Wrong credentials", null, CONV_ID);

            verifyNoInteractions(rentLedgerApplicationService);
        }

        @Test
        void handleResult_disbursementNotFound_throwsRentLedgerStateException() {
            UUID missingId = UUID.randomUUID();
            when(disbursementRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThrows(RentLedgerStateException.class,
                    () -> service.handleResult(missingId, "0", "Success", TXN_ID, CONV_ID));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private Disbursement buildPendingDisbursement() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        d.markPending(OCID);
        return d;
    }

    private Disbursement buildPendingDisbursementWithNullEntry() {
        // ledgerEntryId = null — ad-hoc disbursement not linked to an entry
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, null, AMOUNT, PHONE, NAME, COMMAND_ID);
        d.markPending(OCID);
        return d;
    }

    private Disbursement buildSuccessDisbursement() {
        Disbursement d = buildPendingDisbursement();
        d.markSuccess(TXN_ID, CONV_ID);
        return d;
    }
}
