package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Renter consent toggle for WhatsApp broadcasts. Opt-in is explicit,
 * never assumed: the renter checks the box in the portal, and broadcast
 * WhatsApp delivery is only attempted for profiles where it is enabled.
 * Renters without opt-in still receive SMS/email/in-app deliveries.
 */
public record UpdateWhatsAppOptInRequest(
        @NotNull(message = "enabled is required") Boolean enabled
) {
}
