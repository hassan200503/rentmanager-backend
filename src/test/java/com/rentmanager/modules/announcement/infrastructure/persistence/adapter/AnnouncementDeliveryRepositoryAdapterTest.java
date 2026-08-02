package com.rentmanager.modules.announcement.infrastructure.persistence.adapter;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementDeliveryJpaEntity;
import com.rentmanager.modules.announcement.infrastructure.persistence.mapper.AnnouncementDeliveryPersistenceMapper;
import com.rentmanager.modules.announcement.infrastructure.persistence.repository.AnnouncementDeliveryJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Persistence contract for delivery rows: every field - announcement,
 * renter, channel, status, sent/read timestamps, retry state - survives
 * the round trip, and the due-query only ever selects PENDING/FAILED
 * rows whose backoff has elapsed.
 */
class AnnouncementDeliveryRepositoryAdapterTest {

    private AnnouncementDeliveryJpaRepository jpaRepository;
    private AnnouncementDeliveryPersistenceMapper mapper;
    private AnnouncementDeliveryRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(AnnouncementDeliveryJpaRepository.class);
        mapper = new AnnouncementDeliveryPersistenceMapper();
        adapter = new AnnouncementDeliveryRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    void savePersistsAllFields() {
        UUID tenantId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        UUID renterId = UUID.randomUUID();
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, announcementId, renterId, AnnouncementChannel.SMS);

        when(jpaRepository.save(any())).thenAnswer(inv -> {
            AnnouncementDeliveryJpaEntity entity = inv.getArgument(0);
            entity.restoreId(delivery.getId());
            return entity;
        });

        AnnouncementDelivery result = adapter.save(delivery);

        ArgumentCaptor<AnnouncementDeliveryJpaEntity> captor = ArgumentCaptor.forClass(AnnouncementDeliveryJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        AnnouncementDeliveryJpaEntity entity = captor.getValue();

        assertEquals(delivery.getId(), entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals(announcementId, entity.getAnnouncementId());
        assertEquals(renterId, entity.getRenterProfileId());
        assertEquals(AnnouncementChannel.SMS, entity.getChannel());
        assertEquals(AnnouncementDeliveryStatus.PENDING, entity.getStatus());
        assertEquals(0, entity.getAttemptCount());
        assertNotNull(entity.getNextAttemptAt());
        assertEquals(delivery.getId(), result.getId(), "round trip keeps the id");
    }

    @Test
    void skippedNoOptInRowPersistsTerminalState() {
        UUID tenantId = UUID.randomUUID();
        UUID announcementId = UUID.randomUUID();
        AnnouncementDelivery delivery = AnnouncementDelivery.createSkippedNoOptIn(
                tenantId, announcementId, UUID.randomUUID());

        when(jpaRepository.save(any())).thenAnswer(inv -> {
            AnnouncementDeliveryJpaEntity entity = inv.getArgument(0);
            entity.restoreId(delivery.getId());
            return entity;
        });

        adapter.save(delivery);

        ArgumentCaptor<AnnouncementDeliveryJpaEntity> captor = ArgumentCaptor.forClass(AnnouncementDeliveryJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertEquals(AnnouncementChannel.WHATSAPP, captor.getValue().getChannel());
        assertEquals(AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN, captor.getValue().getStatus());
    }

    @Test
    void findDueSelectsOnlyPendingAndFailedRowsAndCapsToLimit() {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        when(jpaRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                any(), any()))
                .thenReturn(List.of(
                        entity(AnnouncementDeliveryStatus.PENDING),
                        entity(AnnouncementDeliveryStatus.FAILED),
                        entity(AnnouncementDeliveryStatus.PENDING)));

        List<AnnouncementDelivery> due = adapter.findDue(now, 2);

        assertEquals(2, due.size());
        verify(jpaRepository).findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                List.of(AnnouncementDeliveryStatus.PENDING, AnnouncementDeliveryStatus.FAILED), now);
    }

    @Test
    void saveAllPersistsEveryRow() {
        AnnouncementDelivery first = AnnouncementDelivery.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.SMS);
        AnnouncementDelivery second = AnnouncementDelivery.createSkippedNoOptIn(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        when(jpaRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<AnnouncementDelivery> saved = adapter.saveAll(List.of(first, second));

        assertEquals(2, saved.size());
    }

    private AnnouncementDeliveryJpaEntity entity(AnnouncementDeliveryStatus status) {
        AnnouncementDeliveryJpaEntity entity = new AnnouncementDeliveryJpaEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                AnnouncementChannel.SMS,
                status,
                null,
                null,
                0,
                Instant.now(),
                null
        );
        entity.restoreId(UUID.randomUUID());
        entity.assignTenantIfUnset(UUID.randomUUID());
        return entity;
    }
}
