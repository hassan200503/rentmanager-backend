package com.rentmanager.modules.tax.infrastructure.persistence.adapter;

import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.domain.repository.MriRateScheduleRepository;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.MriRateScheduleJpaEntity;
import com.rentmanager.modules.tax.infrastructure.persistence.mapper.MriRateSchedulePersistenceMapper;
import com.rentmanager.modules.tax.infrastructure.persistence.repository.MriRateScheduleJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MriRateScheduleRepositoryAdapter implements MriRateScheduleRepository {

    private final MriRateScheduleJpaRepository jpaRepository;
    private final MriRateSchedulePersistenceMapper mapper;

    @Override
    public Optional<MriRateSchedule> findActiveAsOf(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        return jpaRepository.findByStatusOrderByEffectiveFromDesc(
                        com.rentmanager.modules.tax.domain.enums.MriRateScheduleStatus.ACTIVE)
                .stream()
                .map(mapper::toDomain)
                .filter(schedule -> schedule.isEffectiveOn(date))
                .findFirst();
    }

    @Override
    public Optional<MriRateSchedule> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<MriRateSchedule> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public MriRateSchedule save(MriRateSchedule schedule) {
        MriRateScheduleJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(schedule));
        return mapper.toDomain(saved);
    }
}