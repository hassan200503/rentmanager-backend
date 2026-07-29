package com.rentmanager.modules.rentledger.domain;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the {@code Disbursement} domain model's state transitions.
 *
 * Key finding from the trace phase: {@code Disbursement.markSuccess()} and
 * {@code markFailed()} have NO guard in the domain model itself — they
 * blindly overwrite status. The idempotency guard for duplicate callbacks
 * lives in {@code B2CDisbursementService.handleResult()}, not here.
 *
 * These tests verify:
 *   - Every legal transition succeeds (INITIATED→PENDING, PENDING→SUCCESS,
 *     PENDING→FAILED)
 *   - The domain object's field state after each transition is correct
 *   - {@code create()} always yields INITIATED status
 *   - {@code rehydrate()} reconstructs a disbursement faithfully from
 *     all fields (persistence round-trip invariant)
 *
 * What is NOT tested here: the duplicate-callback guard. That is tested
 * in {@code B2CDisbursementServiceTest} where the guard actually lives.
 */
class DisbursementStateMachineTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("5000.00");
    private static final String PHONE = "+254712345678";
    private static final String NAME = "Test Recipient";
    private static final String COMMAND_ID = "BusinessPayment";

    // ─────────────────────────────────────────────────────────────────────
    // Factory / initial state
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void create_setsInitiatedStatus() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);

        assertEquals(DisbursementStatus.INITIATED, d.getStatus());
        assertNotNull(d.getId());
        assertEquals(TENANT_ID, d.getTenantId());
        assertEquals(LEASE_ID, d.getLeaseId());
        assertEquals(ENTRY_ID, d.getLedgerEntryId());
        assertEquals(new BigDecimal("5000.00"), d.getAmount());
        assertEquals(PHONE, d.getRecipientPhone());
        assertEquals(NAME, d.getRecipientName());
        assertEquals(COMMAND_ID, d.getCommandId());
        assertNotNull(d.getCreatedAt());
        assertNotNull(d.getUpdatedAt());
    }

    @Test
    void create_scalesAmountToTwoDecimalPlaces() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID,
                new BigDecimal("5000.555"), PHONE, NAME, COMMAND_ID);
        // HALF_UP rounding: 5000.555 → 5000.56
        assertEquals(new BigDecimal("5000.56"), d.getAmount());
    }

    @Test
    void create_assignsUniqueIds_eachInvocation() {
        Disbursement d1 = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        Disbursement d2 = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        assertNotEquals(d1.getId(), d2.getId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // INITIATED → PENDING
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void markPending_fromInitiated_setsStatusAndOriginatorConversationId() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        String ocId = "ocid-" + UUID.randomUUID();

        d.markPending(ocId);

        assertEquals(DisbursementStatus.PENDING, d.getStatus());
        assertEquals(ocId, d.getMpesaOriginatorConversationId());
        assertNotNull(d.getUpdatedAt());
    }

    // ─────────────────────────────────────────────────────────────────────
    // PENDING → SUCCESS
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void markSuccess_fromPending_setsStatusAndTransactionFields() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        d.markPending("ocid-123");

        String txnId = "TXN" + UUID.randomUUID();
        String convId = "conv-" + UUID.randomUUID();

        d.markSuccess(txnId, convId);

        assertEquals(DisbursementStatus.SUCCESS, d.getStatus());
        assertEquals(txnId, d.getMpesaTransactionId());
        assertEquals(convId, d.getMpesaConversationId());
        assertNull(d.getFailureReason());
        assertNotNull(d.getUpdatedAt());
    }

    // ─────────────────────────────────────────────────────────────────────
    // PENDING → FAILED
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void markFailed_fromPending_setsStatusAndReason() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        d.markPending("ocid-456");

        String reason = "Insufficient funds";
        String convId = "conv-failed-" + UUID.randomUUID();

        d.markFailed(reason, convId);

        assertEquals(DisbursementStatus.FAILED, d.getStatus());
        assertEquals(reason, d.getFailureReason());
        assertEquals(convId, d.getMpesaConversationId());
        assertNull(d.getMpesaTransactionId());
        assertNotNull(d.getUpdatedAt());
    }

    @Test
    void markFailed_withNullConversationId_doesNotThrow() {
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);
        d.markPending("ocid-789");

        assertDoesNotThrow(() -> d.markFailed("Timeout", null));
        assertEquals(DisbursementStatus.FAILED, d.getStatus());
        assertNull(d.getMpesaConversationId());
    }

    // ─────────────────────────────────────────────────────────────────────
    // INITIATED → FAILED (Daraja initiation error path)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void markFailed_fromInitiated_setsStatusAndReason() {
        // B2CDisbursementService.initiateDisbursement() calls markFailed if
        // DarajaB2CService throws before markPending is ever called.
        Disbursement d = Disbursement.create(TENANT_ID, LEASE_ID, ENTRY_ID, AMOUNT, PHONE, NAME, COMMAND_ID);

        d.markFailed("Initiation failed: connection refused", null);

        assertEquals(DisbursementStatus.FAILED, d.getStatus());
        assertEquals("Initiation failed: connection refused", d.getFailureReason());
    }

    // ─────────────────────────────────────────────────────────────────────
    // rehydrate — persistence round-trip
    // ─────────────────────────────────────────────────────────────────────

    @Test
    void rehydrate_restoresAllFieldsFaithfully() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("12345.67");
        String phone = "+254798765432";
        String name = "Rehydrated User";
        String commandId = "SalaryPayment";
        DisbursementStatus status = DisbursementStatus.SUCCESS;
        String txnId = "TXNABCDEF";
        String convId = "CONV123";
        String ocId = "OCID456";
        String failureReason = null;
        Instant now = Instant.now();
        Instant later = now.plusSeconds(30);

        Disbursement d = Disbursement.rehydrate(
                id, tenantId, leaseId, entryId,
                amount, phone, name, commandId,
                status, txnId, convId, ocId,
                failureReason, 0, false,
                now, later,
                0L
        );

        assertEquals(id, d.getId());
        assertEquals(tenantId, d.getTenantId());
        assertEquals(leaseId, d.getLeaseId());
        assertEquals(entryId, d.getLedgerEntryId());
        assertEquals(amount, d.getAmount());
        assertEquals(phone, d.getRecipientPhone());
        assertEquals(name, d.getRecipientName());
        assertEquals(commandId, d.getCommandId());
        assertEquals(status, d.getStatus());
        assertEquals(txnId, d.getMpesaTransactionId());
        assertEquals(convId, d.getMpesaConversationId());
        assertEquals(ocId, d.getMpesaOriginatorConversationId());
        assertNull(d.getFailureReason());
        assertEquals(0, d.getRetryCount());
        assertFalse(d.isRequiresManualAttention());
        assertEquals(now, d.getCreatedAt());
        assertEquals(later, d.getUpdatedAt());
    }

    @Test
    void rehydrate_preservesFailedStatus_withReason() {
        Disbursement d = Disbursement.rehydrate(
                UUID.randomUUID(), TENANT_ID, LEASE_ID, ENTRY_ID,
                AMOUNT, PHONE, NAME, COMMAND_ID,
                DisbursementStatus.FAILED,
                null, "CONV-FAIL", "OCID-FAIL",
                "Queue timeout", 1, false,
                Instant.now(), Instant.now(),
                0L
        );

        assertEquals(DisbursementStatus.FAILED, d.getStatus());
        assertEquals("Queue timeout", d.getFailureReason());
        assertNull(d.getMpesaTransactionId());
        assertEquals(1, d.getRetryCount());
    }
}
