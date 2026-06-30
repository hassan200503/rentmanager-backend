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

    // Production base URL
    private String baseUrl = "https://api.safaricom.co.ke";
}