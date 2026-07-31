package com.rentmanager.modules.tenant.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public class SubscriptionSwitchRequest {

    @NotBlank(message = "Plan code is required")
    private String planCode;

    @NotBlank(message = "M-Pesa phone number is required")
    private String mpesaPhone;

    public String getPlanCode() { return planCode; }
    public String getMpesaPhone() { return mpesaPhone; }

    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public void setMpesaPhone(String mpesaPhone) { this.mpesaPhone = mpesaPhone; }
}
