package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A request to pay a landlord out.
 *
 * <h2>What is deliberately absent</h2>
 * This record used to carry {@code recipientPhone} and {@code recipientName},
 * which the service passed to Daraja unchanged. Any OWNER or MANAGER could
 * therefore send any amount to any phone number in Kenya — and under platform
 * billing the money came from pooled float belonging to other landlords and
 * their tenants.
 *
 * <p>The destination is now read from {@code tenants.payout_phone_number},
 * exactly as the automatic payout in the M-Pesa callback has always done. A
 * request that cannot name a destination cannot redirect one.
 *
 * <p>{@code ledgerEntryId} is required rather than optional because the
 * amount is validated against that entry's remaining proceeds. Without it
 * there is nothing to check the amount against, which is the situation this
 * change exists to end.
 */
public record InitiateB2CDisbursementRequest(

        @NotNull(message = "leaseId is required")
        UUID leaseId,

        @NotNull(message = "ledgerEntryId is required — the payout is validated against it")
        UUID ledgerEntryId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        String commandId,

        String remarks
) {}
