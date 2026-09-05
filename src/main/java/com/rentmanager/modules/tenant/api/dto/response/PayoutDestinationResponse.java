package com.rentmanager.modules.tenant.api.dto.response;

/**
 * The payout destination, never in full.
 *
 * <p>{@code maskedPhoneNumber} is null when none is configured, which the
 * settings screen uses to prompt for one: a landlord on platform-custody
 * billing whose payout number is unset will never receive a disbursement.
 */
public record PayoutDestinationResponse(
        boolean configured,
        String maskedPhoneNumber
) {}
