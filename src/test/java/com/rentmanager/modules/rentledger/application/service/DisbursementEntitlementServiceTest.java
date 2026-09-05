package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The ceiling that turns the payout amount from "any number the caller sent"
 * into "at most what this charge actually produced and has not already been
 * paid out".
 */
class DisbursementEntitlementServiceTest {

    private RentTransactionRepository rentTransactionRepository;
    private DisbursementRepository disbursementRepository;
    private DisbursementEntitlementService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        rentTransactionRepository = mock(RentTransactionRepository.class);
        disbursementRepository = mock(DisbursementRepository.class);
        service = new DisbursementEntitlementService(
                rentTransactionRepository, disbursementRepository);
    }

    private RentTransaction payment(UUID id, String gross, String net, UUID reverses) {
        RentTransaction tx = mock(RentTransaction.class);
        when(tx.getId()).thenReturn(id);
        when(tx.getType()).thenReturn(RentTransactionType.PAYMENT);
        when(tx.getAmount()).thenReturn(new BigDecimal(gross));
        when(tx.getNetAmount()).thenReturn(net == null ? null : new BigDecimal(net));
        when(tx.getReversesTransactionId()).thenReturn(reverses);
        return tx;
    }

    private Disbursement disbursement(String amount, DisbursementStatus status) {
        Disbursement d = mock(Disbursement.class);
        when(d.getAmount()).thenReturn(new BigDecimal(amount));
        when(d.getStatus()).thenReturn(status);
        return d;
    }

    @Test
    void entitlementIsTheNetProceedsWhenNothingHasBeenPaidOut() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("14250");
    }

    /**
     * Commission is deducted before the landlord is paid, so the ceiling must
     * be the net figure the callback stamped on the transaction — never the
     * gross the tenant sent.
     */
    @Test
    void usesNetAmountNotGrossSoCommissionIsNotPaidOutToTheLandlord() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isLessThan(new BigDecimal("15000"));
    }

    /**
     * A PREMIUM_MONTHLY landlord pays no commission, so the callback stamps no
     * net amount and the gross is the net.
     */
    @Test
    void fallsBackToGrossWhenNoCommissionWasRecorded() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", null, null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("15000");
    }

    @Test
    void alreadyDisbursedAmountsReduceWhatRemains() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        Disbursement paid = disbursement("10000", DisbursementStatus.SUCCESS);
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of(paid));

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("4250");
    }

    /**
     * A payout still settling is not free money. Treating INITIATED and
     * PENDING as uncommitted is exactly how the same proceeds go out twice
     * while the first attempt is in flight.
     */
    @Test
    void payoutsInFlightCountAgainstTheCeiling() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        Disbursement initiated = disbursement("7000", DisbursementStatus.INITIATED);
        Disbursement pending = disbursement("7000", DisbursementStatus.PENDING);
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId))
                .thenReturn(List.of(initiated, pending));

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("250");
    }

    @Test
    void failedPayoutsDoNotCountBecauseNoMoneyLeft() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        Disbursement failed = disbursement("14250", DisbursementStatus.FAILED);
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of(failed));

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("14250");
    }

    /**
     * Payments are never deleted — a REVERSAL posts a compensating row
     * pointing back at the original (V71). Both rows have to drop out, or a
     * landlord could be paid out of money that was handed back.
     */
    @Test
    void reversedPaymentsProduceNoEntitlement() {
        UUID originalId = UUID.randomUUID();
        RentTransaction original = payment(originalId, "15000", "14250", null);
        RentTransaction reversal = mock(RentTransaction.class);
        when(reversal.getId()).thenReturn(UUID.randomUUID());
        when(reversal.getType()).thenReturn(RentTransactionType.REVERSAL);
        when(reversal.getReversesTransactionId()).thenReturn(originalId);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId))
                .thenReturn(List.of(original, reversal));
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("0");
    }

    @Test
    void nonPaymentTransactionsAreNotProceeds() {
        RentTransaction charge = mock(RentTransaction.class);
        when(charge.getType()).thenReturn(RentTransactionType.RENT_CHARGE);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(charge));
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("0");
    }

    @Test
    void anEntryWithNoPaymentsYieldsNothingToDisburse() {
        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of());
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of());

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("0");
    }

    /**
     * Never negative. A negative ceiling could be turned back into headroom by
     * a subtraction somewhere downstream.
     */
    @Test
    void overDisbursedEntryReportsZeroRatherThanANegativeCeiling() {
        RentTransaction tx = payment(UUID.randomUUID(), "15000", "14250", null);

        when(rentTransactionRepository.findByLedgerEntry(tenantId, entryId)).thenReturn(List.of(tx));
        Disbursement overpaid = disbursement("20000", DisbursementStatus.SUCCESS);
        when(disbursementRepository.findByLedgerEntryId(tenantId, entryId)).thenReturn(List.of(overpaid));

        assertThat(service.settleableAmount(tenantId, entryId))
                .isEqualByComparingTo("0");
    }
}
