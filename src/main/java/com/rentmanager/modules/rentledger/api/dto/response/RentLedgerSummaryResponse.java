package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.application.dto.RentLedgerSummary;

/**
 * Wire shape for the landlord dashboard's financial figures.
 *
 * <p>Money is serialised as a String, following the convention established
 * when backend defect #10 was closed: a BigDecimal sent as a JSON number
 * becomes a double in the browser, and "collected this month" is exactly the
 * figure a landlord would notice being a cent out.
 *
 * <p>No trend or percentage-change field, deliberately. A trend needs a prior
 * period to compare against; the card this replaces carried
 * {@code trend: {value: 12, positive: true}} as a literal, so it rendered a
 * green "+12%" next to a zero balance regardless of what had happened.
 */
public record RentLedgerSummaryResponse(
        String collectedThisMonth,
        String outstandingTotal,
        String overdueTotal,
        long overdueEntryCount,
        String currency
) {
    public static RentLedgerSummaryResponse from(RentLedgerSummary summary) {
        return new RentLedgerSummaryResponse(
                summary.collectedThisMonth().toPlainString(),
                summary.outstandingTotal().toPlainString(),
                summary.overdueTotal().toPlainString(),
                summary.overdueEntryCount(),
                summary.currency()
        );
    }
}
