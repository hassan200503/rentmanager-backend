package com.rentmanager.modules.maintenance.application;

import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestQueryService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4a/5: Requests-hub filters/sort/enrichment and the SLA summary
 * gates - an "excellent" response time is only claimed once the tenant
 * has at least MIN_RESOLVED_REQUESTS_FOR_SLA resolved requests.
 */
class MaintenanceRequestQueryServiceTest {

    private MaintenanceRequestRepository requestRepository;
    private PropertyRepository propertyRepository;
    private UnitRepository unitRepository;
    private TenantProfileRepository tenantProfileRepository;
    private MaintenanceRequestQueryService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID propertyId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        requestRepository = mock(MaintenanceRequestRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        unitRepository = mock(UnitRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        service = new MaintenanceRequestQueryService(
                requestRepository, propertyRepository, unitRepository, tenantProfileRepository);
    }

    @Test
    void enrichesResponsesWithPropertyUnitAndRenterNames() {
        MaintenanceRequest request = request("Leaky tap", MaintenancePriority.HIGH, MaintenanceRequestStatus.SUBMITTED);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(request));

        Property property = mock(Property.class);
        when(property.getName()).thenReturn("Sunrise Apartments");
        when(propertyRepository.findByIdAndTenantId(propertyId, tenantId)).thenReturn(Optional.of(property));

        Unit unit = mock(Unit.class);
        when(unit.getUnitNumber()).thenReturn("B2");
        when(unitRepository.findById(unitId)).thenReturn(Optional.of(unit));

        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getFullName()).thenReturn("John Doe");
        when(tenantProfileRepository.findById(tenantProfileId)).thenReturn(Optional.of(profile));

        var responses = service.getRequests(tenantId, null, null, null, null);

        assertEquals(1, responses.size());
        assertEquals("Sunrise Apartments", responses.get(0).propertyName());
        assertEquals("B2", responses.get(0).unitNumber());
        assertEquals("John Doe", responses.get(0).renterName());
    }

    @Test
    void toleratesMissingEnrichmentRecords() {
        MaintenanceRequest request = request("Leaky tap", MaintenancePriority.HIGH, MaintenanceRequestStatus.SUBMITTED);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(request));
        when(propertyRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        when(unitRepository.findById(any())).thenReturn(Optional.empty());
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        var responses = service.getRequests(tenantId, null, null, null, null);

        assertEquals(1, responses.size());
        assertNull(responses.get(0).propertyName());
        assertNull(responses.get(0).unitNumber());
        assertNull(responses.get(0).renterName());
    }

    @Test
    void delegatesFilteredQueriesWhenStatusOrPriorityGiven() {
        service.getRequests(tenantId, MaintenanceRequestStatus.IN_PROGRESS, null, null, null);
        verify(requestRepository).findByTenantIdAndStatus(tenantId, MaintenanceRequestStatus.IN_PROGRESS);

        service.getRequests(tenantId, null, MaintenancePriority.URGENT, null, null);
        verify(requestRepository).findByTenantIdAndPriority(tenantId, MaintenancePriority.URGENT);

        service.getRequests(tenantId, MaintenanceRequestStatus.COMPLETED, MaintenancePriority.LOW, null, null);
        verify(requestRepository).findByTenantIdAndPriorityAndStatus(
                tenantId, MaintenancePriority.LOW, MaintenanceRequestStatus.COMPLETED);
    }

    @Test
    void sortsByPriorityDescendingByDefault() {
        MaintenanceRequest low = request("low", MaintenancePriority.LOW, MaintenanceRequestStatus.SUBMITTED);
        MaintenanceRequest urgent = request("urgent", MaintenancePriority.URGENT, MaintenanceRequestStatus.SUBMITTED);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(low, urgent));

        var responses = service.getRequests(tenantId, null, null, "priority", null);

        assertEquals("urgent", responses.get(0).title());
        assertEquals("low", responses.get(1).title());
    }

    @Test
    void sortsAscendingWhenRequested() {
        MaintenanceRequest a = request("A", MaintenancePriority.LOW, MaintenanceRequestStatus.SUBMITTED);
        MaintenanceRequest b = request("B", MaintenancePriority.LOW, MaintenanceRequestStatus.SUBMITTED);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(b, a));

        var responses = service.getRequests(tenantId, null, null, "title", "asc");

        assertEquals("A", responses.get(0).title());
        assertEquals("B", responses.get(1).title());
    }

    @Test
    void slaSummaryHidesRatingBelowResolvedThreshold() {
        MaintenanceRequest completed = requestWithResponseHours("done", MaintenanceRequestStatus.COMPLETED, 5);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(completed));

        var summary = service.getSlaSummary(tenantId);

        assertEquals(1, summary.totalRequests());
        assertEquals(1, summary.resolvedRequests());
        assertFalse(summary.resolvedRequirementMet());
        assertNull(summary.responseRatePct());
    }

    @Test
    void slaSummaryShowsRateWhenThresholdMet() {
        MaintenanceRequest r1 = requestWithResponseHours("r1", MaintenanceRequestStatus.COMPLETED, 2);
        MaintenanceRequest r2 = requestWithResponseHours("r2", MaintenanceRequestStatus.COMPLETED, 3);
        MaintenanceRequest r3 = requestWithResponseHours("r3", MaintenanceRequestStatus.COMPLETED, 4);
        MaintenanceRequest r4 = requestWithResponseHours("r4", MaintenanceRequestStatus.COMPLETED, 10);
        MaintenanceRequest r5 = requestWithResponseHours("r5", MaintenanceRequestStatus.COMPLETED, 48);

        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(r1, r2, r3, r4, r5));

        var summary = service.getSlaSummary(tenantId);

        assertEquals(5, summary.resolvedRequests());
        assertTrue(summary.resolvedRequirementMet());
        assertNotNull(summary.responseRatePct());
        assertEquals(80, summary.responseRatePct());
    }

    @Test
    void slaSummaryCountsOnlyCompletedAsResolved() {
        MaintenanceRequest completed = requestWithResponseHours("done", MaintenanceRequestStatus.COMPLETED, 1);
        MaintenanceRequest pending = requestWithResponseHours("todo", MaintenanceRequestStatus.SUBMITTED, 1);
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of(completed, pending));

        var summary = service.getSlaSummary(tenantId);

        assertEquals(1, summary.resolvedRequests());
        assertEquals(2, summary.respondedRequests());
    }

    @Test
    void slaSummaryIsZeroWhenNoRequests() {
        when(requestRepository.findAllByTenantId(tenantId)).thenReturn(List.of());

        var summary = service.getSlaSummary(tenantId);

        assertEquals(0, summary.totalRequests());
        assertEquals(0.0, summary.avgResponseHours());
        assertFalse(summary.resolvedRequirementMet());
        assertNull(summary.responseRatePct());
    }

    @Test
    void countUnviewedDelegatesToRepository() {
        when(requestRepository.countUnviewedByTenantId(tenantId)).thenReturn(3L);

        long count = service.countUnviewed(tenantId);

        assertEquals(3L, count);
        verify(requestRepository).countUnviewedByTenantId(tenantId);
    }

    private MaintenanceRequest request(String title, MaintenancePriority priority, MaintenanceRequestStatus status) {
        return request(title, priority, status, null);
    }

    private MaintenanceRequest requestWithResponseHours(
            String title, MaintenanceRequestStatus status, long responseHours) {
        Instant now = Instant.now();
        Instant createdAt = now.minusSeconds(responseHours * 3600);
        return MaintenanceRequest.rehydrate(
                UUID.randomUUID(), tenantId, unitId, propertyId, tenantProfileId,
                UUID.randomUUID(), title, "desc", MaintenanceCategory.PLUMBING,
                MaintenancePriority.MEDIUM, status,
                null, null,
                LocalDateTime.ofInstant(now, java.time.ZoneOffset.UTC),
                null, null, "renter", null, 0L, createdAt, createdAt);
    }

    private MaintenanceRequest request(
            String title, MaintenancePriority priority, MaintenanceRequestStatus status, LocalDateTime firstResponse) {
        Instant createdAt = firstResponse != null
                ? firstResponse.minusHours(6).toInstant(java.time.ZoneOffset.UTC)
                : Instant.now().minusSeconds(60);
        return MaintenanceRequest.rehydrate(
                UUID.randomUUID(), tenantId, unitId, propertyId, tenantProfileId,
                UUID.randomUUID(), title, "desc", MaintenanceCategory.PLUMBING, priority, status,
                null, null, firstResponse, null, null, "renter", null, 0L, createdAt, createdAt);
    }
}
