package com.rentmanager.modules.announcement.infrastructure.persistence.adapter;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.model.AnnouncementDelivery;
import com.rentmanager.modules.announcement.domain.repository.AnnouncementDeliveryRepository;
import com.rentmanager.modules.announcement.infrastructure.persistence.mapper.AnnouncementDeliveryPersistenceMapper;
import com.rentmanager.modules.announcement.infrastructure.persistence.repository.AnnouncementDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AnnouncementDeliveryRepositoryAdapter implements AnnouncementDeliveryRepository {

    private final AnnouncementDeliveryJpaRepository jpaRepository;
    private final AnnouncementDeliveryPersistenceMapper mapper;

    @Override
    public AnnouncementDelivery save(AnnouncementDelivery delivery) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(delivery)));
    }

    @Override
    public List<AnnouncementDelivery> saveAll(Collection<AnnouncementDelivery> deliveries) {
        return jpaRepository.saveAll(deliveries.stream().map(mapper::toJpaEntity).toList())
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<AnnouncementDelivery> findDue(Instant now, int limit) {
        return jpaRepository
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
                        List.of(AnnouncementDeliveryStatus.PENDING, AnnouncementDeliveryStatus.FAILED),
                        now)
                .stream().limit(limit).map(mapper::toDomain).toList();
    }

    @Override
    public List<AnnouncementDelivery> findByAnnouncementIdAndTenantId(UUID announcementId, UUID tenantId) {
        return jpaRepository.findByAnnouncementIdAndTenantId(announcementId, tenantId)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<AnnouncementDelivery> findByAnnouncementIdAndRenterProfileIdAndChannel(
            UUID announcementId, UUID renterProfileId, AnnouncementChannel channel) {
        return jpaRepository
                .findByAnnouncementIdAndRenterProfileIdAndChannel(announcementId, renterProfileId, channel)
                .map(mapper::toDomain);
    }

    @Override
    public List<AnnouncementDelivery> findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
            UUID tenantId, UUID renterProfileId, Collection<UUID> announcementIds, AnnouncementChannel channel) {
        return jpaRepository
                .findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
                        tenantId, renterProfileId, announcementIds, channel)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countUnreadByTenantIdAndRenterProfileIdAndChannel(
            UUID tenantId, UUID renterProfileId, AnnouncementChannel channel, Instant now) {
        return jpaRepository.countUnreadByTenantIdAndRenterProfileIdAndChannel(
                tenantId, renterProfileId, channel, now);
    }
}
