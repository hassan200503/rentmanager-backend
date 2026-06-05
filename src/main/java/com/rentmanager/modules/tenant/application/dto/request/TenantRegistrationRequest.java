package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Data;

@Data
public class TenantRegistrationRequest {

    private String tenantName;
    private String email;
    private String phoneNumber;

    private String organizationName;
    private String organizationType;

    private String slug;

    private String timezone;
    private String currency;
    private String locale;
}