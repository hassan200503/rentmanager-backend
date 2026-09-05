package com.rentmanager.shared.util.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyFormatterTest {

    @Test
    void rendersKenyanShillingsWithTheSymbolUsedOnMpesaReceipts() {
        assertThat(MoneyFormatter.format(new BigDecimal("12500"), "KES"))
                .isEqualTo("KSh 12,500.00");
    }

    @Test
    void currencyCodeIsCaseInsensitive() {
        assertThat(MoneyFormatter.format(new BigDecimal("1"), "kes")).isEqualTo("KSh 1.00");
    }

    /**
     * A partial payment leaves balances like 4,166.67. Rounding that to
     * 4,167 in a message while the ledger holds the exact figure is how a
     * renter pays the wrong amount and then argues about it.
     */
    @Test
    void keepsTwoDecimalPlacesSoPartialBalancesAreExact() {
        assertThat(MoneyFormatter.format(new BigDecimal("4166.67"), "KES"))
                .isEqualTo("KSh 4,166.67");
    }

    @Test
    void groupsThousandsForLargeAmounts() {
        assertThat(MoneyFormatter.format(new BigDecimal("1234567.5"), "KES"))
                .isEqualTo("KSh 1,234,567.50");
    }

    @Test
    void unknownCurrencyIsShownAsItsCodeRatherThanGuessedAt() {
        assertThat(MoneyFormatter.format(new BigDecimal("50"), "UGX"))
                .isEqualTo("UGX 50.00");
    }

    /**
     * Ledger rows have carried a currency only since V79. A row written
     * before that, or any path that has not been updated to pass one, must
     * still produce a sensible Kenyan message rather than "null 12,500.00".
     */
    @Test
    void missingCurrencyFallsBackToShillingsRatherThanPrintingNull() {
        assertThat(MoneyFormatter.format(new BigDecimal("12500"), null))
                .isEqualTo("KSh 12,500.00");
        assertThat(MoneyFormatter.format(new BigDecimal("12500"), "  "))
                .isEqualTo("KSh 12,500.00");
    }

    @Test
    void nullAmountRendersAsZeroRatherThanThrowingInsideAMessagePath() {
        assertThat(MoneyFormatter.format(null, "KES")).isEqualTo("KSh 0.00");
    }

    @Test
    void formatAmountOmitsTheCurrencyPrefix() {
        assertThat(MoneyFormatter.formatAmount(new BigDecimal("12500"))).isEqualTo("12,500.00");
    }

    /**
     * Locale-independent by construction: the old call sites used
     * NumberFormat with Locale.US, so a JVM started with a different default
     * locale would have produced "12.500,00" on some paths and not others.
     */
    @Test
    void groupingAndDecimalSeparatorsDoNotFollowTheJvmDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            assertThat(MoneyFormatter.format(new BigDecimal("12500.25"), "KES"))
                    .isEqualTo("KSh 12,500.25");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }
}
