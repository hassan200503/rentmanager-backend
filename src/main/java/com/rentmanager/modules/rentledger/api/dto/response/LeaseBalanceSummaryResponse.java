package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Per-lease rent status for the Tenants page — one entry per lease that
 * currently has money outstanding or an unresolved overpayment. A lease
 * with nothing owed simply has no entry in the list (see
 * {@code RentLedgerQueryServiceImpl#getBalanceByLease}), rather than an
 * entry claiming a zero balance, since "not in the list" and "confirmed
 * paid up" both being representable as zero would be indistinguishable
 * from "hasn't loaded yet" on the client.
 */
public record LeaseBalanceSummaryResponse(
        UUID leaseId,
        BigDecimal outstandingBalance,
        String status,
        LocalDate oldestUnpaidDueDate
) {
}
