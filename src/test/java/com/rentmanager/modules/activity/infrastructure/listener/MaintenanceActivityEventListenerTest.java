package com.rentmanager.modules.activity.infrastructure.listener;

import com.rentmanager.modules.activity.application.ActivityLogService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * V54: maintenance submissions must land in the activity feed so the
 * landlord dashboard's SSE/toast/bell pipeline fires - actor is the renter's
 * profile name (the event fires inside the renter-scoped portal request).
 */
class MaintenanceActivityEventListenerTest {

    private ActivityLogService activityLogService;
    private TenantProfileRepository tenantProfileRepository;
    private UnitRepository unitRepository;
    private MaintenanceActivityEventListener listener;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        activityLogService = mock(ActivityLogService.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        unitRepository = mock(UnitRepository.class);
        listener = new MaintenanceActivityEventListener(
                activityLogService, tenantProfileRepository, unitRepository);
    }

    @Test
    void recordsActivityWithRenterActorAndUnitMetadata_onSubmitted() {
        MaintenanceRequestSubmitted event = event("Broken window");

        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getFullName()).thenReturn("Hassan Karungwa");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        Unit unit = mock(Unit.class);
        when(unit.getUnitNumber()).thenReturn("HWSW");
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        listener.onMaintenanceRequestSubmitted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(
                eq(tenantId),
                eq("MAINTENANCE_REQUEST_SUBMITTED"),
                eq("MaintenanceRequest"),
                eq(requestId),
                eq("Broken window"),
                eq(null),
                eq("Hassan Karungwa"),
                metadataCaptor.capture());

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals(unitId.toString(), metadata.get("unitId"));
        assertEquals(propertyId.toString(), metadata.get("propertyId"));
        assertEquals("PLUMBING", metadata.get("category"));
        assertEquals("URGENT", metadata.get("priority"));
        assertEquals("HWSW", metadata.get("unitNumber"));
    }

    @Test
    void fallsBackToRenterLabelAndOmitsUnitNumber_whenLookupsMiss() {
        MaintenanceRequestSubmitted event = event("Leaky tap");

        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.empty());
        when(unitRepository.findById(unitId)).thenReturn(Optional.empty());

        listener.onMaintenanceRequestSubmitted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(
                eq(tenantId),
                eq("MAINTENANCE_REQUEST_SUBMITTED"),
                eq("MaintenanceRequest"),
                eq(requestId),
                eq("Leaky tap"),
                eq(null),
                eq("Renter"),
                metadataCaptor.capture());

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertFalse(metadata.containsKey("unitNumber"));
        assertEquals("PLUMBING", metadata.get("category"));
    }

    @Test
    void omitsBlankUnitNumberFromMetadata() {
        MaintenanceRequestSubmitted event = event("Dirt in the compound");

        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getFullName()).thenReturn("Hassan Karungwa");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        Unit unit = mock(Unit.class);
        when(unit.getUnitNumber()).thenReturn("  ");
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        listener.onMaintenanceRequestSubmitted(event);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityLogService).record(
                any(), any(), any(), any(), any(), any(), any(), metadataCaptor.capture());
        assertFalse(metadataCaptor.getValue().containsKey("unitNumber"));
    }

    @Test
    void neverThrows_whenServiceFails() {
        MaintenanceRequestSubmitted event = event("Electricity");

        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getFullName()).thenReturn("Hassan Karungwa");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));
        when(unitRepository.findById(unitId)).thenReturn(Optional.empty());

        doThrow(new RuntimeException("boom")).when(activityLogService).record(
                any(), any(), any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onMaintenanceRequestSubmitted(event));
    }

    private MaintenanceRequestSubmitted event(String title) {
        return new MaintenanceRequestSubmitted(
                tenantId, requestId, "corr", unitId, propertyId,
                tenantProfileId, leaseId, title, MaintenanceCategory.PLUMBING, MaintenancePriority.URGENT);
    }
}
