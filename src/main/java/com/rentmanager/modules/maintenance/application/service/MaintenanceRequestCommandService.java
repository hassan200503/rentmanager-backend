package com.rentmanager.modules.maintenance.application.service;

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

        request = maintenanceRequestRepository.save(request);
        publish(request);

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
        request = maintenanceRequestRepository.save(request);
        publish(request);

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
        request = maintenanceRequestRepository.save(request);
        publish(request);

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
        request = maintenanceRequestRepository.save(request);
        publish(request);

        log.info("Maintenance request assigned: id={} assignee={}", requestId, assignee);
        return request;
    }

    private void publish(MaintenanceRequest request) {
        var events = request.pullDomainEvents();
        if (!events.isEmpty()) {
            eventPublisher.publishAll(events);
        }
    }
}
