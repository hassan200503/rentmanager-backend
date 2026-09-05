package com.rentmanager.shared.util.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats money for messages a customer will read.
 *
 * <h2>Why this exists</h2>
 * Three notification paths independently reached for
 * {@code NumberFormat.getNumberInstance(Locale.US)} — the rent reminder
 * scheduler and both rent notification listeners. That produces
 * {@code "12,500"}: no currency at all, and US conventions in a Kenyan
 * product. A renter reading "Your rent of 12,500 is due" has to supply the
 * currency themselves, and a landlord operating in more than one currency
 * gets no signal which one they are looking at.
 *
 * <p>Amounts are also rendered with two decimal places rather than truncated.
 * A partial payment leaves balances like {@code 4,166.67}, and rounding that
 * to {@code 4,167} in a message while the ledger holds the exact figure is
 * how a renter pays the wrong amount and then argues about it.
 */
public final class MoneyFormatter {

    private static final DecimalFormatSymbols SYMBOLS =
            DecimalFormatSymbols.getInstance(Locale.ROOT);

    private MoneyFormatter() {
        // utility class — not instantiable
    }

    /**
     * Renders an amount with its currency, e.g. {@code "KSh 12,500.00"}.
     *
     * @param currency ISO 4217 code as stored on the ledger entry. {@code KES}
     *                 renders as {@code KSh}, the form used on M-Pesa
     *                 receipts and therefore the one a Kenyan renter will
     *                 recognise on the message next to it. Any other code is
     *                 rendered as-is rather than guessed at.
     */
    public static String format(BigDecimal amount, String currency) {
        return symbolFor(currency) + " " + formatAmount(amount);
    }

    /** The grouped, two-decimal amount with no currency prefix. */
    public static String formatAmount(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount)
                .setScale(2, RoundingMode.HALF_UP);
        DecimalFormat format = new DecimalFormat("#,##0.00", SYMBOLS);
        return format.format(value);
    }

    private static String symbolFor(String currency) {
        if (currency == null || currency.isBlank()) {
            return "KSh";
        }
        return "KES".equalsIgnoreCase(currency.trim()) ? "KSh" : currency.trim().toUpperCase(Locale.ROOT);
    }
}
