package com.rentmanager.modules.tax.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
        name = "mri_rate_schedule",
        indexes = {
                @Index(name = "idx_mri_rate_schedule_effective", columnList = "effective_from")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class MriRateScheduleJpaEntity extends BaseEntity {

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 4)
    private BigDecimal ratePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MriRateScheduleStatus status;

    @Column(name = "source_reference")
    private String sourceReference;
}