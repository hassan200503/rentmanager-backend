package com.rentmanager.modules.activity.infrastructure.persistence;

import com.rentmanager.modules.activity.domain.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, UUID> {

    Page<ActivityLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<ActivityLog> findByTenantIdAndEntityTypeOrderByCreatedAtDesc(UUID tenantId, String entityType, Pageable pageable);

    Page<ActivityLog> findByTenantIdAndEventTypeOrderByCreatedAtDesc(UUID tenantId, String eventType, Pageable pageable);

    Page<ActivityLog> findByTenantIdAndEntityTypeAndEventTypeOrderByCreatedAtDesc(UUID tenantId, String entityType, String eventType, Pageable pageable);
}