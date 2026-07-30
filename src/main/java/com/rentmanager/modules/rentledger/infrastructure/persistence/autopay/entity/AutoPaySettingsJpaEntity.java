package com.rentmanager.modules.rentledger.infrastructure.persistence.autopay.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "auto_pay_settings")
public class AutoPaySettingsJpaEntity extends BaseTenantEntity {

    @Column(name = "lease_id", nullable = false, unique = true)
    private java.util.UUID leaseId;

    @Column(name = "tenant_profile_id", nullable = false)
    private java.util.UUID tenantProfileId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "mpesa_phone", length = 20)
    private String mpesaPhone;

    @Column(name = "last_auto_pay_date")
    private LocalDate lastAutoPayDate;

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}