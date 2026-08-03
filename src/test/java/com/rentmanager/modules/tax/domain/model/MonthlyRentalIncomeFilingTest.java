package com.rentmanager.modules.tax.domain.model;

import com.rentmanager.modules.tax.domain.enums.MonthlyFilingStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MonthlyRentalIncomeFilingTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Test
    void compute_calculatesTaxDueFromGrossAndRate() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("100000.00"), new BigDecimal("0.0750"));

        assertEquals(new BigDecimal("7500.00"), filing.getMriTaxDue());
        assertEquals(new BigDecimal("100000.00"), filing.getGrossRentalIncome());
        assertEquals(MonthlyFilingStatus.COMPUTED, filing.getStatus());
        assertFalse(filing.isNilReturn());
        assertNotNull(filing.getComputedAt());
        assertNotNull(filing.getId());
    }

    @Test
    void compute_roundsTaxDueToTwoDecimals() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("33333.33"), new BigDecimal("0.0750"));

        assertEquals(new BigDecimal("2500.00"), filing.getMriTaxDue());
    }

    @Test
    void compute_zeroGrossProducesNilReturn() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                BigDecimal.ZERO, new BigDecimal("0.0750"));

        assertTrue(filing.isNilReturn());
        assertEquals(BigDecimal.ZERO.setScale(2), filing.getGrossRentalIncome());
        assertEquals(BigDecimal.ZERO.setScale(2), filing.getMriTaxDue());
    }

    @Test
    void compute_rejectsNonMonthStartPeriodAndNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 15),
                new BigDecimal("100"), new BigDecimal("0.0750")));

        assertThrows(IllegalArgumentException.class, () -> MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("100"), BigDecimal.ZERO));
    }

    @Test
    void markReadyForManualSwitchesForLandlordAction() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("5000"), new BigDecimal("0.0750"));

        filing.markReadyForManual();

        assertEquals(MonthlyFilingStatus.READY_FOR_MANUAL, filing.getStatus());
        assertNull(filing.getNextAttemptAt());
    }

    @Test
    void markTransmitted_recordsTimestampAndClearsRetry() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("5000"), new BigDecimal("0.0750"));

        filing.markTransmitted();

        assertEquals(MonthlyFilingStatus.TRANSMITTED, filing.getStatus());
        assertNotNull(filing.getTransmittedAt());
        assertNotNull(filing.getFiledAt());
        assertNull(filing.getNextAttemptAt());
    }

    @Test
    void markTransmissionFailure_backsOffUntilExhausted() {
        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                TENANT_ID, LocalDate.of(2026, 8, 1),
                new BigDecimal("5000"), new BigDecimal("0.0750"));

        filing.markTransmissionFailure("timeout", 3);
        assertEquals(MonthlyFilingStatus.FAILED, filing.getStatus());
        assertEquals(1, filing.getAttemptCount());
        assertNotNull(filing.getNextAttemptAt());

        filing.markTransmissionFailure("timeout", 3);
        filing.markTransmissionFailure("timeout", 3);

        assertEquals(3, filing.getAttemptCount());
        assertNull(filing.getNextAttemptAt());
        assertFalse(filing.isTransmissionCandidate(LocalDateTime.now(), 3));
    }
}