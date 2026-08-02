package com.rentmanager.modules.maintenance.application.service;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceRequestCommandService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public MaintenanceRequest submit(
            UUID tenantId,
            UUID unitId,
            UUID propertyId,
            UUID tenantProfileId,
            UUID leaseId,
            String title,
            String description,
            MaintenanceCategory category,
            MaintenancePriority priority,
            String createdBy,
            String correlationId
    ) {
        MaintenanceRequest request = MaintenanceRequest.submit(
                tenantId, unitId, propertyId, tenantProfileId, leaseId,
                title, description, category, priority, createdBy, correlationId
        );

        List<DomainEvent> events = request.pullDomainEvents();
        request = maintenanceRequestRepository.save(request);
        eventPublisher.publishAll(events);

        log.info("Maintenance request submitted: id={} tenantId={} unitId={} category={} priority={}",
                request.getId(), tenantId, unitId, category, priority);

        return request;
    }

    @Transactional
    public MaintenanceRequest updateStatus(
            UUID tenantId,
            UUID requestId,
            MaintenanceRequestStatus newStatus,
            String correlationId
    ) {
        MaintenanceRequest request = maintenanceRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance request not found: " + requestId));

        request.changeStatus(newStatus, correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        request = maintenanceRequestRepository.save(request);
        eventPublisher.publishAll(events);

        log.info("Maintenance request status updated: id={} newStatus={}", requestId, newStatus);
        return request;
    }

    @Transactional
    public MaintenanceRequest schedule(
            UUID tenantId,
            UUID requestId,
            LocalDate scheduledDate,
            String correlationId
    ) {
        MaintenanceRequest request = maintenanceRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance request not found: " + requestId));

        request.schedule(scheduledDate, correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        request = maintenanceRequestRepository.save(request);
        eventPublisher.publishAll(events);

        log.info("Maintenance request scheduled: id={} date={}", requestId, scheduledDate);
        return request;
    }

    @Transactional
    public MaintenanceRequest assign(
            UUID tenantId,
            UUID requestId,
            String assignee,
            String correlationId
    ) {
        MaintenanceRequest request = maintenanceRequestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance request not found: " + requestId));

        request.assignTo(assignee, correlationId);
        List<DomainEvent> events = request.pullDomainEvents();
        request = maintenanceRequestRepository.save(request);
        eventPublisher.publishAll(events);

        log.info("Maintenance request assigned: id={} assignee={}", requestId, assignee);
        return request;
    }

    /**
     * V54: marks every unviewed request for the tenant as viewed - fired when
     * the landlord opens the Requests hub. Returns how many were updated.
     */
    @Transactional
    public int markAllViewed(UUID tenantId) {
        int updated = maintenanceRequestRepository.markAllViewedByTenantId(tenantId);
        if (updated > 0) {
            log.info("Maintenance requests marked viewed: tenantId={} count={}", tenantId, updated);
        }
        return updated;
    }
}
