package com.rentmanager.modules.rentledger.application.dto;

import java.math.BigDecimal;

/**
 * The four numbers a landlord opens the dashboard to see.
 *
 * <h2>Why this exists</h2>
 * The landlord dashboard rendered six financial cards from a hard-coded
 * constant array — {@code value: "KES 0"} with {@code trend: {value: 12}},
 * so it displayed a green "+12%" beside a zero. There was no financial
 * aggregate anywhere in the API to wire them to, only per-entry and
 * per-lease lists.
 *
 * <p>Deleting the cards would have been honest but would have left the
 * product unable to answer the question it is bought to answer: how much came
 * in, and how much is still owed. So the aggregate is real now.
 *
 * <h2>Every figure is computed, none is estimated</h2>
 * All four come from {@code rent_ledger_entries} in one query, scoped to the
 * landlord. Nothing here is derived from a formula over counts, and nothing
 * carries a trend — a trend needs a prior period to compare against, and
 * inventing one is what the constant array was doing.
 *
 * @param collectedThisMonth  sum of {@code amount_paid} on entries whose
 *                            billing period starts in the current month.
 *                            What actually arrived, not what was invoiced.
 * @param outstandingTotal    sum of {@code amount_due - amount_paid} across
 *                            every unsettled entry, of any age. The number a
 *                            landlord chases.
 * @param overdueTotal        the portion of the above already past its due
 *                            date and flagged OVERDUE by the nightly sweep.
 * @param overdueEntryCount   how many charges make up that overdue figure —
 *                            "KSh 40,000 across 2 tenants" is actionable in a
 *                            way the amount alone is not.
 * @param currency            ISO code carried from the entries, so the UI
 *                            never has to assume KES.
 */
public record RentLedgerSummary(
        BigDecimal collectedThisMonth,
        BigDecimal outstandingTotal,
        BigDecimal overdueTotal,
        long overdueEntryCount,
        String currency
) {
    /** A landlord with no ledger entries at all — genuinely zero, not unknown. */
    public static RentLedgerSummary empty(String currency) {
        return new RentLedgerSummary(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0L, currency);
    }
}
