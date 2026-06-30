package com.rentmanager.modules.identity.clerk;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "clerk")
public class ClerkProperties {

    private String secretKey;

    // Clerk Backend API base URL
    private String baseUrl = "https://api.clerk.com/v1";
}