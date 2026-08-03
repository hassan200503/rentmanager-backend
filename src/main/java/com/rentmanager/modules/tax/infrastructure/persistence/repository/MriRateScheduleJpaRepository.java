package com.rentmanager.modules.tax.infrastructure.persistence.repository;

import com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.MriRateScheduleJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MriRateScheduleJpaRepository extends JpaRepository<MriRateScheduleJpaEntity, UUID> {

    List<MriRateScheduleJpaEntity> findByStatusOrderByEffectiveFromDesc(MriRateScheduleStatus status);
}
