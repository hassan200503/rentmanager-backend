package com.rentmanager.modules.rentledger.api.dto.response;

/**
 * The renter's WhatsApp broadcast consent state. Persisted on the tenant
 * profile; read back so the portal checkbox reflects reality.
 */
public record WhatsAppOptInResponse(boolean enabled) {
}
