package com.rentmanager.modules.announcement.infrastructure.persistence.repository;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementDeliveryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementDeliveryJpaRepository extends JpaRepository<AnnouncementDeliveryJpaEntity, UUID> {

    List<AnnouncementDeliveryJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAt(
            List<AnnouncementDeliveryStatus> statuses, Instant now);

    List<AnnouncementDeliveryJpaEntity> findByAnnouncementIdAndTenantId(UUID announcementId, UUID tenantId);

    Optional<AnnouncementDeliveryJpaEntity> findByAnnouncementIdAndRenterProfileIdAndChannel(
            UUID announcementId, UUID renterProfileId, AnnouncementChannel channel);

    List<AnnouncementDeliveryJpaEntity> findByTenantIdAndRenterProfileIdAndAnnouncementIdInAndChannel(
            UUID tenantId, UUID renterProfileId, Collection<UUID> announcementIds, AnnouncementChannel channel);

    /**
     * The renter's unread in-app badge count, restricted to announcements
     * that are not yet expired (a.readAt null checks the delivery, expiry
     * checks the parent announcement).
     */
    @Query("""
            select count(d)
            from AnnouncementDeliveryJpaEntity d
            join AnnouncementJpaEntity a on a.id = d.announcementId
            where d.tenantId = :tenantId
              and d.renterProfileId = :renterProfileId
              and d.channel = :channel
              and d.readAt is null
              and (a.expiresAt is null or a.expiresAt > :now)
            """)
    long countUnreadByTenantIdAndRenterProfileIdAndChannel(
            @Param("tenantId") UUID tenantId,
            @Param("renterProfileId") UUID renterProfileId,
            @Param("channel") AnnouncementChannel channel,
            @Param("now") Instant now);
}
