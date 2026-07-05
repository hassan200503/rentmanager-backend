package com.rentmanager.modules.tenant.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigureDarajaCredentialsRequest {

    @NotBlank
    private String consumerKey;

    @NotBlank
    private String consumerSecret;

    @NotBlank
    private String businessShortCode;

    @NotBlank
    private String passkey;
}