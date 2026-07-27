package com.rentmanager.modules.rentledger.infrastructure.daraja;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "daraja.b2c")
public class DarajaB2CProperties {

    private String initiatorName;
    private String securityCredential;
    private String resultUrl;
    private String queueTimeOutUrl;
    private String callbackSecret;
}