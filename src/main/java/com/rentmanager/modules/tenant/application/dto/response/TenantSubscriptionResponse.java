package com.rentmanager.modules.tenant.application.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TenantSubscriptionResponse {

    private String status;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate trialEndDate;

    private boolean autoRenew;

    private BigDecimal amount;

    private String planCode;
}