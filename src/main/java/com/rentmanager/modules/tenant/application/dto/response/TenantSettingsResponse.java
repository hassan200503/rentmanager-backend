package com.rentmanager.modules.tenant.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantSettingsResponse {

    private String timezone;
    private String currency;
    private String locale;

    private boolean emailNotificationsEnabled;
    private boolean smsNotificationsEnabled;
    private boolean pushNotificationsEnabled;

    private boolean maintenanceModuleEnabled;
    private boolean accountingModuleEnabled;
    private boolean analyticsModuleEnabled;
    private boolean automationModuleEnabled;
}