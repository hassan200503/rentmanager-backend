package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.domain.enums.VatTreatment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaxInvoiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TRANSACTION_ID = UUID.randomUUID();
    private static final UUID LEDGER_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();
    private static final int MAX_ATTEMPTS = 3;

    private TaxInvoice newInvoice() {
        return TaxInvoice.create(
                TENANT_ID, TRANSACTION_ID, LEDGER_ID, LEASE_ID, null,
                "P000000000A", "P111111111K",
                PremisesType.RESIDENTIAL, VatTreatment.VAT_EXEMPT,
                new BigDecimal("15000.00"), "EXT-1", "MPESA",
                LocalDateTime.of(2026, 8, 3, 10, 30));
    }

    @Test
    void create_setsPendingWithImmediateFirstAttempt() {
        TaxInvoice invoice = newInvoice();

        assertEquals(TaxInvoiceStatus.PENDING, invoice.getStatus());
        assertEquals(0, invoice.getAttemptCount());
        assertNotNull(invoice.getNextAttemptAt());
        assertFalse(invoice.getNextAttemptAt().isAfter(LocalDateTime.now()));
        assertNotNull(invoice.getId());
        assertFalse(invoice.getLandlordKraPin().isBlank());
    }

    @Test
    void create_rejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> TaxInvoice.create(
                null, TRANSACTION_ID, LEDGER_ID, LEASE_ID, null,
                "A", null, PremisesType.RESIDENTIAL, VatTreatment.VAT_EXEMPT,
                new BigDecimal("100"), null, "MPESA", LocalDateTime.now()));

        assertThrows(IllegalArgumentException.class, () -> TaxInvoice.create(
                TENANT_ID, null, LEDGER_ID, LEASE_ID, null,
                "A", null, PremisesType.RESIDENTIAL, VatTreatment.VAT_EXEMPT,
                new BigDecimal("100"), null, "MPESA", LocalDateTime.now()));

        assertThrows(IllegalArgumentException.class, () -> TaxInvoice.create(
                TENANT_ID, TRANSACTION_ID, LEDGER_ID, LEASE_ID, null,
                "A", null, null, VatTreatment.VAT_EXEMPT,
                new BigDecimal("100"), null, "MPESA", LocalDateTime.now()));
    }

    @Test
    void create_rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> TaxInvoice.create(
                TENANT_ID, TRANSACTION_ID, LEDGER_ID, LEASE_ID, null,
                "A", null, PremisesType.RESIDENTIAL, VatTreatment.VAT_EXEMPT,
                new BigDecimal("-1"), null, "MPESA", LocalDateTime.now()));
    }

    @Test
    void markTransmitted_capturesReceiptAndStopsRetries() {
        TaxInvoice invoice = newInvoice();

        invoice.markTransmitted("CTRL-001", "QR-DATA", "SIG", "SRN-1");

        assertEquals(TaxInvoiceStatus.TRANSMITTED, invoice.getStatus());
        assertEquals("CTRL-001", invoice.getKraControlNumber());
        assertEquals("SRN-1", invoice.getSequentialReceiptNumber());
        assertEquals(1, invoice.getAttemptCount());
        assertNull(invoice.getNextAttemptAt());
        assertNotNull(invoice.getTransmittedAt());
    }

    @Test
    void markTransmitted_isIdempotent() {
        TaxInvoice invoice = newInvoice();
        invoice.markTransmitted("C1", "Q", "S", "R");
        invoice.markTransmitted("C2", "Q2", "S2", "R2");

        assertEquals("C1", invoice.getKraControlNumber());
        assertEquals(1, invoice.getAttemptCount());
    }

    @Test
    void markTransmissionFailure_schedulesBackoffUntilMaxAttempts() {
        TaxInvoice invoice = newInvoice();

        invoice.markTransmissionFailure("kra timeout", MAX_ATTEMPTS);
        assertEquals(TaxInvoiceStatus.FAILED, invoice.getStatus());
        assertEquals(1, invoice.getAttemptCount());
        assertEquals("kra timeout", invoice.getLastError());
        assertNotNull(invoice.getNextAttemptAt());

        invoice.markTransmissionFailure("kra timeout again", MAX_ATTEMPTS);
        assertEquals(2, invoice.getAttemptCount());
        assertNotNull(invoice.getNextAttemptAt());
    }

    @Test
    void markTransmissionFailure_clearsNextAttemptAtWhenExhausted() {
        TaxInvoice invoice = newInvoice();

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            invoice.markTransmissionFailure("boom", MAX_ATTEMPTS);
        }

        assertEquals(MAX_ATTEMPTS, invoice.getAttemptCount());
        assertNull(invoice.getNextAttemptAt());
        assertFalse(invoice.isTransmissionCandidate(LocalDateTime.now(), MAX_ATTEMPTS));
    }

    @Test
    void isTransmissionCandidate_onlyWhenDue() {
        TaxInvoice invoice = newInvoice();
        assertTrue(invoice.isTransmissionCandidate(LocalDateTime.now(), MAX_ATTEMPTS));

        invoice.markTransmitted("C", "Q", "S", "R");
        assertFalse(invoice.isTransmissionCandidate(LocalDateTime.now(), MAX_ATTEMPTS));
    }

    @Test
    void markSelfFiled_isTerminalAndNotRetriable() {
        TaxInvoice invoice = newInvoice();

        invoice.markSelfFiled();

        assertEquals(TaxInvoiceStatus.SELF_FILED, invoice.getStatus());
        assertNull(invoice.getNextAttemptAt());
        assertFalse(invoice.isTransmissionCandidate(LocalDateTime.now(), MAX_ATTEMPTS));
    }
}