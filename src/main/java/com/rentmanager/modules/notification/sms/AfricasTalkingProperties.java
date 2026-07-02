package com.rentmanager.modules.notification.sms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "africastalking")
public class AfricasTalkingProperties {

    private boolean enabled;
    private String apiKey;
    private String username;
    private String senderId;

    // Production base URL
    private String baseUrl = "https://api.africastalking.com/version1/messaging";
}