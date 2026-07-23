package com.rentmanager.modules.reservation.infrastructure.daraja;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "daraja")
public class DarajaProperties {

    private String consumerKey;
    private String consumerSecret;
    private String businessShortCode;
    private String passkey;
    private String callbackUrl;

    // Distinct callback URL for the rent-payment flow (see
    // RentPaymentController's public callback endpoint). Kept as a
    // separate property rather than derived by string-editing callbackUrl,
    // since the two flows' controllers live under different paths
    // (/api/v1/public/reservations vs /api/v1/public/rent-ledger) and this
    // makes each explicit and independently configurable. Both share the
    // same callbackSecret below — one platform trust boundary, not two.
    private String rentPaymentCallbackUrl;

    // Shared secret embedded as a path segment in callbackUrl, validated on
    // receipt by ReservationController#mpesaCallback. Daraja does not sign
    // callback payloads, so this is the only thing gating that endpoint from
    // the open internet. Generate with e.g. `openssl rand -hex 32` and set
    // via DARAJA_CALLBACK_SECRET — never commit a real value to application.yml.
    private String callbackSecret;

    // Production base URL
    private String baseUrl = "https://api.safaricom.co.ke";
}