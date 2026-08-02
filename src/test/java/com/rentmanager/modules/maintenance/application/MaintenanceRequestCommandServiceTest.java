package com.rentmanager.modules.maintenance.application;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The write side must not lose domain events: the repository adapter
 * persists and returns a REHYDRATED instance (see
 * MaintenanceRequestRepositoryAdapter.save -> mapper.toDomain), so events
 * pulled from the returned object would be empty and the AFTER_COMMIT
 * listeners (activity feed, Phase 5 notifications) would never fire.
 */
class MaintenanceRequestCommandServiceTest {

    private MaintenanceRequest rehydrateLikeAdapter(MaintenanceRequest saved) {
        return MaintenanceRequest.rehydrate(
                saved.getId(), saved.getTenantId(), saved.getUnitId(), saved.getPropertyId(),
                saved.getTenantProfileId(), saved.getLeaseId(), saved.getTitle(), saved.getDescription(),
                saved.getCategory(), saved.getPriority(), saved.getStatus(), saved.getScheduledDate(),
                saved.getCompletedAt(), saved.getFirstLandlordResponseAt(), saved.getLandlordViewedAt(),
                saved.getNotes(), saved.getCreatedBy(), saved.getAssignedTo(),
                saved.getVersion(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    @Test
    void submittedEventIsPublishedEvenWhenSaveReturnsRehydratedInstance() {
        MaintenanceRequestRepository repository = mock(MaintenanceRequestRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MaintenanceRequestCommandService service = new MaintenanceRequestCommandService(repository, publisher);

        UUID tenantId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        when(repository.save(any())).thenAnswer(inv -> rehydrateLikeAdapter(inv.getArgument(0)));

        service.submit(tenantId, unitId, propertyId, null, null,
                "Leaky tap", "Kitchen tap drips", MaintenanceCategory.PLUMBING,
                MaintenancePriority.HIGH, "renter-user", "corr-1");

        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher).publishAll(captor.capture());
        List<DomainEvent> published = captor.getValue();
        assertEquals(1, published.size(), "the submitted event must survive the save round-trip");
        assertTrue(published.get(0) instanceof MaintenanceRequestSubmitted);
        MaintenanceRequestSubmitted event = (MaintenanceRequestSubmitted) published.get(0);
        assertEquals(tenantId, event.getTenantId());
        assertEquals("Leaky tap", event.getTitle());
        assertEquals(MaintenanceCategory.PLUMBING, event.getCategory());
    }

    @Test
    void statusChangedEventIsPublishedOnUpdateStatus() {
        MaintenanceRequestRepository repository = mock(MaintenanceRequestRepository.class);
        DomainEventPublisher publisher = mock(DomainEventPublisher.class);
        MaintenanceRequestCommandService service = new MaintenanceRequestCommandService(repository, publisher);

        UUID tenantId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        MaintenanceRequest found = MaintenanceRequest.rehydrate(
                requestId, tenantId, null, null, null, null,
                "Broken door", "Handle came off", MaintenanceCategory.STRUCTURAL,
                MaintenancePriority.MEDIUM, MaintenanceRequestStatus.SUBMITTED,
                null, null, null, null, null, "renter-user", null, 0L, null, null);

        when(repository.findByIdAndTenantId(requestId, tenantId)).thenReturn(Optional.of(found));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(tenantId, requestId, MaintenanceRequestStatus.IN_PROGRESS, "corr-2");

        ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher).publishAll(captor.capture());
        List<DomainEvent> published = captor.getValue();
        assertEquals(1, published.size());
        assertTrue(published.get(0) instanceof MaintenanceRequestStatusChanged);
        MaintenanceRequestStatusChanged event = (MaintenanceRequestStatusChanged) published.get(0);
        assertEquals(MaintenanceRequestStatus.SUBMITTED, event.getOldStatus());
        assertEquals(MaintenanceRequestStatus.IN_PROGRESS, event.getNewStatus());
    }
}
