package com.rentmanager.modules.tenant.application.dto.request;

import com.rentmanager.modules.tenant.domain.enums.BillingCycle;

import java.math.BigDecimal;

public class SubscriptionPlanRequest {

    private String code;
    private String name;
    private String description;
    private BillingCycle billingCycle;
    private Integer maxProperties;
    private Integer maxUnits;
    private Integer maxUsers;
    private Integer maxStorageGb;
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BillingCycle getBillingCycle() { return billingCycle; }
    public Integer getMaxProperties() { return maxProperties; }
    public Integer getMaxUnits() { return maxUnits; }
    public Integer getMaxUsers() { return maxUsers; }
    public Integer getMaxStorageGb() { return maxStorageGb; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public BigDecimal getYearlyPrice() { return yearlyPrice; }
}