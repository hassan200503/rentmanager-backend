package com.rentmanager.modules.notification.infrastructure.persistence.adapter;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import com.rentmanager.modules.notification.domain.repository.NotificationDeliveryRepository;
import com.rentmanager.modules.notification.infrastructure.persistence.entity.NotificationDeliveryJpaEntity;
import com.rentmanager.modules.notification.infrastructure.persistence.mapper.NotificationDeliveryPersistenceMapper;
import com.rentmanager.modules.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryAdapter implements NotificationDeliveryRepository {

    private final NotificationDeliveryJpaRepository jpaRepository;
    private final NotificationDeliveryPersistenceMapper mapper;

    @Override
    public NotificationDelivery save(NotificationDelivery delivery) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(delivery)));
    }

    @Override
    public List<NotificationDelivery> findDue(Instant now, int limit) {
        return jpaRepository
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                        List.of(NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.FAILED),
                        now)
                .stream().limit(limit).map(mapper::toDomain).toList();
    }
}
