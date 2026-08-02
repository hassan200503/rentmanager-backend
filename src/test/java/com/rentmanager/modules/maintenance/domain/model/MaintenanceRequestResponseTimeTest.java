package com.rentmanager.modules.maintenance.domain.model;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4a: first_landlord_response_at is captured exactly once on the
 * first landlord status mutation and never overwritten - it is the
 * response-time SLA measurement point.
 */
class MaintenanceRequestResponseTimeTest {

    private final UUID tenantId = UUID.randomUUID();

    private MaintenanceRequest submitted() {
        return MaintenanceRequest.submit(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Leaky tap", "Kitchen sink dripping",
                MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH,
                "renter@example.com", "corr-1");
    }

    @Test
    void responseTimeIsNullUntilFirstStatusChange() {
        MaintenanceRequest request = submitted();

        assertNull(request.getFirstLandlordResponseAt());
    }

    @Test
    void capturesResponseTimeOnFirstStatusChange() {
        MaintenanceRequest request = submitted();

        request.changeStatus(MaintenanceRequestStatus.IN_REVIEW, "corr-2");

        assertNotNull(request.getFirstLandlordResponseAt());
    }

    @Test
    void doesNotOverwriteResponseTimeOnLaterChanges() {
        MaintenanceRequest request = submitted();

        request.changeStatus(MaintenanceRequestStatus.IN_REVIEW, "corr-2");
        java.time.LocalDateTime first = request.getFirstLandlordResponseAt();

        request.changeStatus(MaintenanceRequestStatus.IN_PROGRESS, "corr-3");
        request.changeStatus(MaintenanceRequestStatus.COMPLETED, "corr-4");

        assertEquals(first, request.getFirstLandlordResponseAt());
    }

    @Test
    void noOpStatusChangeDoesNotCaptureResponseTime() {
        MaintenanceRequest request = submitted();

        request.changeStatus(MaintenanceRequestStatus.SUBMITTED, "corr-2");

        assertNull(request.getFirstLandlordResponseAt());
    }

    @Test
    void scheduleCapturesResponseTimeViaStatusChange() {
        MaintenanceRequest request = submitted();

        request.schedule(java.time.LocalDate.now().plusDays(1), "corr-2");

        assertNotNull(request.getFirstLandlordResponseAt());
        assertEquals(MaintenanceRequestStatus.SCHEDULED, request.getStatus());
    }

    @Test
    void assignCapturesResponseTimeViaStatusChange() {
        MaintenanceRequest request = submitted();

        request.assignTo("fixer@example.com", "corr-2");

        assertNotNull(request.getFirstLandlordResponseAt());
        assertEquals(MaintenanceRequestStatus.IN_REVIEW, request.getStatus());
    }

    @Test
    void submittedRequestIsUnviewedUntilMarked() {
        MaintenanceRequest request = submitted();

        assertNull(request.getLandlordViewedAt());
    }

    @Test
    void markViewedSetsTimestampExactlyOnce() {
        MaintenanceRequest request = submitted();

        request.markViewed();
        java.time.LocalDateTime first = request.getLandlordViewedAt();

        request.markViewed();

        assertEquals(first, request.getLandlordViewedAt());
    }

    @Test
    void statusChangeImpliesViewed() {
        MaintenanceRequest request = submitted();

        request.changeStatus(MaintenanceRequestStatus.IN_REVIEW, "corr-2");

        assertNotNull(request.getLandlordViewedAt());
    }

    @Test
    void noOpStatusChangeDoesNotMarkViewed() {
        MaintenanceRequest request = submitted();

        request.changeStatus(MaintenanceRequestStatus.SUBMITTED, "corr-2");

        assertNull(request.getLandlordViewedAt());
    }
}
