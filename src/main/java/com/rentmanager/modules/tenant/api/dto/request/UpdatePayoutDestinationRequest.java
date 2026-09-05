package com.rentmanager.modules.tenant.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * The M-Pesa number a landlord's rent is paid out to.
 *
 * <p>Validated to Kenyan MSISDN in {@code +2547XXXXXXXX} form, matching what
 * Daraja B2C requires and what the reservation flow already enforces.
 * Rejecting a malformed number here is far cheaper than discovering it when a
 * payout fails at the provider — by which point the rent has been collected
 * from the renter and the landlord is waiting for money that will not arrive.
 */
public record UpdatePayoutDestinationRequest(

        @NotBlank(message = "A payout number is required")
        @Pattern(
                regexp = "^\\+2547\\d{8}$",
                message = "Enter a Safaricom number in the form +2547XXXXXXXX"
        )
        String payoutPhoneNumber
) {}
