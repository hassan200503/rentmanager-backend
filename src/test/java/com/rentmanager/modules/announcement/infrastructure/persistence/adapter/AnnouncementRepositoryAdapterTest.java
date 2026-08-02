package com.rentmanager.modules.announcement.infrastructure.persistence.adapter;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementJpaEntity;
import com.rentmanager.modules.announcement.infrastructure.persistence.mapper.AnnouncementPersistenceMapper;
import com.rentmanager.modules.announcement.infrastructure.persistence.repository.AnnouncementJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Persistence contract: every announcement field survives the save/load
 * round trip through the JPA layer - message, priority, channels,
 * expiry, tenant and author are all mapped, and every query stays
 * tenant-scoped.
 */
class AnnouncementRepositoryAdapterTest {

    private AnnouncementJpaRepository jpaRepository;
    private AnnouncementPersistenceMapper mapper;
    private AnnouncementRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(AnnouncementJpaRepository.class);
        mapper = new AnnouncementPersistenceMapper();
        adapter = new AnnouncementRepositoryAdapter(jpaRepository, mapper);
    }

    @Test
    void savePersistsAllFields() {
        UUID tenantId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(86400);
        Announcement announcement = Announcement.create(
                tenantId, UUID.randomUUID(), "Water shutoff Sunday 8-11am",
                AnnouncementPriority.URGENT,
                EnumSet.of(AnnouncementChannel.IN_APP, AnnouncementChannel.SMS, AnnouncementChannel.EMAIL, AnnouncementChannel.WHATSAPP),
                expiresAt, "corr");

        when(jpaRepository.save(any())).thenAnswer(inv -> {
            AnnouncementJpaEntity entity = inv.getArgument(0);
            entity.restoreId(announcement.getId());
            return entity;
        });

        Announcement result = adapter.save(announcement);

        ArgumentCaptor<AnnouncementJpaEntity> captor = ArgumentCaptor.forClass(AnnouncementJpaEntity.class);
        verify(jpaRepository).save(captor.capture());
        AnnouncementJpaEntity entity = captor.getValue();

        assertEquals(announcement.getId(), entity.getId());
        assertEquals(tenantId, entity.getTenantId());
        assertEquals(announcement.getAuthorId(), entity.getAuthorId());
        assertEquals("Water shutoff Sunday 8-11am", entity.getMessage());
        assertEquals(AnnouncementPriority.URGENT, entity.getPriority());
        assertEquals("IN_APP,SMS,EMAIL,WHATSAPP", entity.getChannels());
        assertEquals(expiresAt, entity.getExpiresAt());
        assertEquals(announcement.getId(), result.getId(), "round trip keeps the id");
    }

    @Test
    void findByIdAndTenantIdIsTenantScoped() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(jpaRepository.findByIdAndTenantId(id, tenantId))
                .thenReturn(Optional.of(jpaEntity(id, tenantId)));

        Optional<Announcement> found = adapter.findByIdAndTenantId(id, tenantId);

        assertTrue(found.isPresent());
        assertEquals(tenantId, found.get().getTenantId());
        assertEquals(AnnouncementPriority.INFO, found.get().getPriority());
        assertTrue(found.get().getChannels().contains(AnnouncementChannel.IN_APP));
        assertTrue(found.get().getChannels().contains(AnnouncementChannel.WHATSAPP));
        assertEquals("Rent due on the 1st", found.get().getMessage());
    }

    @Test
    void findActiveByTenantIdPassesNowThrough() {
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        when(jpaRepository.findActiveByTenantId(tenantId, now)).thenReturn(java.util.List.of());

        adapter.findActiveByTenantId(tenantId, now);

        verify(jpaRepository).findActiveByTenantId(eq(tenantId), eq(now));
    }

    private AnnouncementJpaEntity jpaEntity(UUID id, UUID tenantId) {
        AnnouncementJpaEntity entity = new AnnouncementJpaEntity(
                UUID.randomUUID(),
                "Rent due on the 1st",
                AnnouncementPriority.INFO,
                "IN_APP,SMS,WHATSAPP",
                null
        );
        entity.restoreId(id);
        entity.assignTenantIfUnset(tenantId);
        return entity;
    }
}
