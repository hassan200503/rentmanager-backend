package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Data;

@Data
public class UpdateTenantSettingsRequest {

    private String timezone;
    private String currency;
    private String locale;

    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private Boolean pushNotificationsEnabled;

    private Boolean maintenanceModuleEnabled;
    private Boolean accountingModuleEnabled;
    private Boolean analyticsModuleEnabled;
    private Boolean automationModuleEnabled;

    private Boolean allowCustomBranding;
    private Boolean allowApiAccess;
}