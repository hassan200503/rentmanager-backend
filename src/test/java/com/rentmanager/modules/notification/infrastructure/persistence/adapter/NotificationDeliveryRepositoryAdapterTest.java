package com.rentmanager.modules.notification.infrastructure.persistence.adapter;

import com.rentmanager.modules.notification.domain.model.NotificationDelivery;
import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import com.rentmanager.modules.notification.infrastructure.persistence.entity.NotificationDeliveryJpaEntity;
import com.rentmanager.modules.notification.infrastructure.persistence.mapper.NotificationDeliveryPersistenceMapper;
import com.rentmanager.modules.notification.infrastructure.persistence.repository.NotificationDeliveryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5: the sweep must re-select FAILED rows once the backoff has
 * elapsed - a query that only ever returns PENDING would make the whole
 * retry chain dead in production.
 */
class NotificationDeliveryRepositoryAdapterTest {

    private NotificationDeliveryJpaRepository jpaRepository;
    private NotificationDeliveryPersistenceMapper mapper;
    private NotificationDeliveryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(NotificationDeliveryJpaRepository.class);
        mapper = mock(NotificationDeliveryPersistenceMapper.class);
        adapter = new NotificationDeliveryRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    void findDueSelectsPendingAndFailedRows() {
        Instant now = Instant.parse("2026-08-01T10:00:00Z");
        when(jpaRepository
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                        eq(List.of(NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.FAILED)),
                        eq(now)))
                .thenReturn(List.of(entity(NotificationDeliveryStatus.PENDING), entity(NotificationDeliveryStatus.FAILED)));
        when(mapper.toDomain(any())).thenReturn(mock(NotificationDelivery.class));

        adapter.findDue(now, 100);

        verify(jpaRepository).findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                eq(List.of(NotificationDeliveryStatus.PENDING, NotificationDeliveryStatus.FAILED)),
                eq(now));
    }

    @Test
    void findDueCapsResultsToLimit() {
        Instant now = Instant.parse("2026-08-01T10:00:00Z");
        when(jpaRepository
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(any(), any()))
                .thenReturn(List.of(entity(NotificationDeliveryStatus.PENDING),
                        entity(NotificationDeliveryStatus.FAILED),
                        entity(NotificationDeliveryStatus.PENDING)));
        when(mapper.toDomain(any())).thenReturn(mock(NotificationDelivery.class));

        List<NotificationDelivery> due = adapter.findDue(now, 2);

        assertEquals(2, due.size());
    }

    private NotificationDeliveryJpaEntity entity(NotificationDeliveryStatus status) {
        return new NotificationDeliveryJpaEntity(
                UUID.randomUUID(), UUID.randomUUID(), null, "+254712345678",
                null, "Hello", null, status, 0, null, null);
    }
}
