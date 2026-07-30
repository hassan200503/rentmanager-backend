package com.rentmanager.modules.maintenance.infrastructure.persistence.mapper;

import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.infrastructure.persistence.entity.MaintenanceRequestJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceRequestPersistenceMapper {

    public MaintenanceRequestJpaEntity toJpaEntity(MaintenanceRequest request) {
        if (request == null) return null;

        MaintenanceRequestJpaEntity jpa = new MaintenanceRequestJpaEntity();
        jpa.setId(request.getId());
        jpa.assignTenantIfUnset(request.getTenantId());
        jpa.setVersion(request.getVersion());
        jpa.setUnitId(request.getUnitId());
        jpa.setPropertyId(request.getPropertyId());
        jpa.setTenantProfileId(request.getTenantProfileId());
        jpa.setLeaseId(request.getLeaseId());
        jpa.setTitle(request.getTitle());
        jpa.setDescription(request.getDescription());
        jpa.setCategory(request.getCategory());
        jpa.setPriority(request.getPriority());
        jpa.setStatus(request.getStatus());
        jpa.setScheduledDate(request.getScheduledDate());
        jpa.setCompletedAt(request.getCompletedAt());
        jpa.setNotes(request.getNotes());
        jpa.setCreatedBy(request.getCreatedBy());
        jpa.setAssignedTo(request.getAssignedTo());
        return jpa;
    }

    public MaintenanceRequest toDomain(MaintenanceRequestJpaEntity jpa) {
        if (jpa == null) return null;

        return MaintenanceRequest.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getUnitId(),
                jpa.getPropertyId(),
                jpa.getTenantProfileId(),
                jpa.getLeaseId(),
                jpa.getTitle(),
                jpa.getDescription(),
                jpa.getCategory(),
                jpa.getPriority(),
                jpa.getStatus(),
                jpa.getScheduledDate(),
                jpa.getCompletedAt(),
                jpa.getNotes(),
                jpa.getCreatedBy(),
                jpa.getAssignedTo(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}
