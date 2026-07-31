package com.rentmanager.modules.tenant.application.dto.response;

import com.rentmanager.modules.tenant.domain.enums.BillingCycle;

import java.math.BigDecimal;
import java.util.UUID;

public class SubscriptionPlanResponse {

    private UUID id;
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
    private boolean active;
    private boolean selfService;

    public SubscriptionPlanResponse(
            UUID id,
            String code,
            String name,
            String description,
            BillingCycle billingCycle,
            Integer maxProperties,
            Integer maxUnits,
            Integer maxUsers,
            Integer maxStorageGb,
            BigDecimal monthlyPrice,
            BigDecimal yearlyPrice,
            boolean active,
            boolean selfService
    ) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.billingCycle = billingCycle;
        this.maxProperties = maxProperties;
        this.maxUnits = maxUnits;
        this.maxUsers = maxUsers;
        this.maxStorageGb = maxStorageGb;
        this.monthlyPrice = monthlyPrice;
        this.yearlyPrice = yearlyPrice;
        this.active = active;
        this.selfService = selfService;
    }

    public UUID getId() { return id; }
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
    public boolean isActive() { return active; }
    public boolean isSelfService() { return selfService; }
}