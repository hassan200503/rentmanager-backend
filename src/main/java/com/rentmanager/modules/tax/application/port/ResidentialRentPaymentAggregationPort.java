package com.rentmanager.modules.tax.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Aggregates residential rent payments for the MRI computation.
 *
 * <p>Implementation joins rent_transactions -> rent_ledger_entries ->
 * units -> properties and sums PAYMENT-type transactions that fall inside
 * the period for RESIDENTIAL premises only. COMMERCIAL rent never enters
 * the MRI regime (Finance Act 2023 wording) — the filtering lives in the
 * query itself so no caller can forget it.
 */
public interface ResidentialRentPaymentAggregationPort {

    /**
     * Sum of residential rent payments (type = PAYMENT) recorded in
     * [periodStart, periodEndExclusive) for the landlord.
     */
    BigDecimal sumResidentialPaymentsInPeriod(
            UUID tenantId,
            LocalDate periodStart,
            LocalDate periodEndExclusive
    );

    /**
     * All landlords that recorded at least one residential rent payment in
     * the period (drives the monthly filing scheduler).
     */
    java.util.List<UUID> findTenantIdsWithResidentialPaymentsInPeriod(
            LocalDate periodStart,
            LocalDate periodEndExclusive
    );
}
