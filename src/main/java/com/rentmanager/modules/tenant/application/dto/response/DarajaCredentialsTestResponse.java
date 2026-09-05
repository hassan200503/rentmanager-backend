package com.rentmanager.modules.tenant.application.dto.response;

/**
 * Outcome of a real connection test against Safaricom using a landlord's
 * saved Daraja credentials.
 *
 * <p>{@code message} and {@code error} carry the provider's own verdict, not
 * a paraphrase — a landlord debugging a rejected key needs Safaricom's actual
 * error code, and inventing friendlier wording here would hide it.
 *
 * @param ok          whether Safaricom issued an OAuth token
 * @param message     human-readable outcome
 * @param error       provider error detail; null when {@code ok}
 * @param environment the Safaricom host that answered, so a sandbox host
 *                    responding in production is visible rather than implied
 * @param scope       what the test did and did not prove — the Consumer
 *                    Key/Secret are verified, the Shortcode and Passkey are
 *                    not, because Safaricom only exercises those when an STK
 *                    push is actually signed
 */
public record DarajaCredentialsTestResponse(
        boolean ok,
        String message,
        String error,
        String environment,
        String scope
) {
    public static final String SCOPE_NOTE =
            "Verifies the Consumer Key and Secret only. The Shortcode and Passkey "
                    + "are not checked — Safaricom exercises those only when a real "
                    + "payment prompt is signed.";
}
