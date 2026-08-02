package com.rentmanager.modules.announcement.infrastructure.persistence.adapter;

import com.rentmanager.modules.announcement.domain.model.Announcement;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementRepository;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementJpaEntity;
import com.rentmanager.modules.announcement.infrastructure.persistence.mapper.AnnouncementPersistenceMapper;
import com.rentmanager.modules.announcement.infrastructure.persistence.repository.AnnouncementJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnnouncementRepositoryAdapter implements AnnouncementRepository {

    private final AnnouncementJpaRepository jpaRepository;
    private final AnnouncementPersistenceMapper mapper;

    @Override
    public Announcement save(Announcement announcement) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(announcement)));
    }

    @Override
    public Optional<Announcement> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<Announcement> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Announcement> findActiveByTenantId(UUID tenantId, Instant now) {
        return jpaRepository.findActiveByTenantId(tenantId, now)
                .stream().map(mapper::toDomain).toList();
    }
}
