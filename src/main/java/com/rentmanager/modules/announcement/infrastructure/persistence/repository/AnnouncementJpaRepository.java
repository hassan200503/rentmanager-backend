package com.rentmanager.modules.announcement.infrastructure.persistence.repository;

import com.rentmanager.modules.announcement.infrastructure.persistence.entity.AnnouncementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnnouncementJpaRepository extends JpaRepository<AnnouncementJpaEntity, UUID> {

    Optional<AnnouncementJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<AnnouncementJpaEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Non-expired announcements for the tenant, newest first - the renter
     * portal list. Expired ones are hidden automatically.
     */
    @Query("""
            SELECT a FROM AnnouncementJpaEntity a
             WHERE a.tenantId = :tenantId
               AND (a.expiresAt IS NULL OR a.expiresAt > :now)
             ORDER BY a.createdAt DESC
            """)
    List<AnnouncementJpaEntity> findActiveByTenantId(@Param("tenantId") UUID tenantId, @Param("now") Instant now);
}
