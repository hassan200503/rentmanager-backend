package com.rentmanager.modules.tax.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.tax.domain.enums.MonthlyFilingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "monthly_rental_income_filings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_monthly_filing_per_period",
                        columnNames = {"tenant_id", "period"}
                )
        },
        indexes = {
                @Index(name = "idx_monthly_filings_due", columnList = "status, attempt_count, next_attempt_at"),
                @Index(name = "idx_monthly_filings_tenant_period", columnList = "tenant_id, period")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MonthlyRentalIncomeFilingJpaEntity extends BaseTenantEntity {

    @Column(name = "period", nullable = false)
    private LocalDate period;

    @Column(name = "gross_rental_income", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossRentalIncome;

    @Column(name = "is_nil_return", nullable = false)
    private boolean isNilReturn;

    @Column(name = "mri_rate_applied", nullable = false, precision = 5, scale = 4)
    private BigDecimal mriRateApplied;

    @Column(name = "mri_tax_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal mriTaxDue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MonthlyFilingStatus status;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "transmitted_at")
    private LocalDateTime transmittedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;
}