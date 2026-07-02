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

    // Shared secret embedded as a path segment in callbackUrl, validated on
    // receipt by ReservationController#mpesaCallback. Daraja does not sign
    // callback payloads, so this is the only thing gating that endpoint from
    // the open internet. Generate with e.g. `openssl rand -hex 32` and set
    // via DARAJA_CALLBACK_SECRET — never commit a real value to application.yml.
    private String callbackSecret;

    // Production base URL
    private String baseUrl = "https://api.safaricom.co.ke";
}