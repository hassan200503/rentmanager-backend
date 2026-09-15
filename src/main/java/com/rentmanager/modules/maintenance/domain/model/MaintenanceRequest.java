package com.rentmanager.modules.maintenance.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MaintenanceRequest extends AggregateRoot {

    private UUID unitId;
    private UUID propertyId;
    private UUID tenantProfileId;
    private UUID leaseId;
    private String title;
    private String description;
    private MaintenanceCategory category;
    private MaintenancePriority priority;
    private MaintenanceRequestStatus status;
    private LocalDate scheduledDate;
    private Instant completedAt;

    /**
     * Phase 4a/5: timestamp of the FIRST landlord response (any status
     * mutation by the landlord side: updateStatus/schedule/assign). Set
     * once, never overwritten - it is exactly what the response-time SLA
     * badge measures (firstLandlordResponseAt - createdAt).
     */
    private Instant firstLandlordResponseAt;

    /**
     * V54: timestamp of when the landlord first saw this request (opened the
     * Requests hub, or acted on it via any status mutation). NULL while the
     * request is still unviewed - that drives the sidebar badge count. Set
     * once, never overwritten.
     */
    private Instant landlordViewedAt;

    private String notes;
    private String createdBy;
    private String assignedTo;

    public static MaintenanceRequest submit(
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
        MaintenanceRequest request = MaintenanceRequest.builder()
                .unitId(unitId)
                .propertyId(propertyId)
                .tenantProfileId(tenantProfileId)
                .leaseId(leaseId)
                .title(title)
                .description(description)
                .category(category)
                .priority(priority)
                .status(MaintenanceRequestStatus.SUBMITTED)
                .createdBy(createdBy)
                .build();

        request.assignTenant(tenantId);

        request.registerEvent(new MaintenanceRequestSubmitted(
                tenantId, request.getId(), correlationId,
                unitId, propertyId, tenantProfileId, leaseId,
                title, category, priority
        ));

        return request;
    }

    public void changeStatus(MaintenanceRequestStatus newStatus, String correlationId) {
        changeStatus(newStatus, null, correlationId);
    }

    /**
     * Moves the request on, optionally with a message for the renter.
     *
     * <p><b>Why the message matters.</b> Until it existed, "responding" to a
     * renter meant changing an enum. Someone who reported a locked water tank
     * received an SMS reading "Update on Maintenance Request: in review" and
     * nothing else — no what, no when, no who. The status told them their
     * report had been noticed; it could not tell them anything they actually
     * wanted to know. The note is the reply, and it travels with the status
     * change rather than sitting in a separate channel nobody checks.
     *
     * <p>Kept on the aggregate rather than as a separate comment thread
     * deliberately: a thread is a bigger feature with its own read state,
     * ordering and notification rules, and most replies here are one line
     * attached to a transition. If two-way conversation is needed later, this
     * is the field it grows out of, not something it has to fight.
     */
    public void changeStatus(MaintenanceRequestStatus newStatus, String landlordNote, String correlationId) {
        MaintenanceRequestStatus oldStatus = this.status;
        String note = landlordNote == null || landlordNote.isBlank() ? null : landlordNote.trim();

        // A note alone is a valid response: "still sourcing the part" without
        // a status change is exactly the update a waiting renter wants, and
        // refusing it would push landlords into making meaningless status
        // moves to be able to say anything at all.
        if (oldStatus == newStatus && note == null) {
            return;
        }

        if (newStatus == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (!oldStatus.canMoveTo(newStatus)) {
            throw new IllegalStateException(transitionRefusal(oldStatus, newStatus));
        }
        if (oldStatus == MaintenanceRequestStatus.CANCELLED) {
            // Terminal: not even a note. A reply on a cancelled request would
            // notify the renter about something that is closed.
            throw new IllegalStateException("This request was cancelled and can't be updated.");
        }

        this.status = newStatus;
        if (note != null) {
            this.notes = note;
        }

        // Phase 4a: capture the first landlord response timestamp exactly
        // once. Status mutations on this aggregate only come from
        // landlord-gated endpoints (status/schedule/assign), so the first
        // transition out of SUBMITTED is the landlord's first response.
        //
        // CANCELLED is excluded: closing a request without ever addressing it
        // is not a response, and counting it as one let the SLA be improved
        // by discarding work — the opposite of what the metric is for.
        if (this.firstLandlordResponseAt == null && newStatus != MaintenanceRequestStatus.CANCELLED) {
            this.firstLandlordResponseAt = Instant.now();
        }

        // V54: any landlord status mutation means the landlord has seen the
        // request - clear the "unviewed" flag as a side effect.
        markViewed();

        if (newStatus == MaintenanceRequestStatus.COMPLETED) {
            this.completedAt = Instant.now();
        }

        registerEvent(new MaintenanceRequestStatusChanged(
                getTenantId(), getId(), correlationId,
                tenantProfileId, oldStatus, newStatus, title, note
        ));
    }

    /**
     * V54: records that the landlord has seen this request. Set once, never
     * overwritten.
     */
    public void markViewed() {
        if (this.landlordViewedAt == null) {
            this.landlordViewedAt = Instant.now();
        }
    }

    private static String transitionRefusal(MaintenanceRequestStatus from, MaintenanceRequestStatus to) {
        if (to == MaintenanceRequestStatus.SUBMITTED) {
            return "A request can't be moved back to Submitted. Send a message instead, or move it to In review.";
        }
        if (from == MaintenanceRequestStatus.CANCELLED) {
            return "This request was cancelled and can't be updated.";
        }
        if (from == MaintenanceRequestStatus.COMPLETED) {
            return "A completed request can only be reopened as In progress.";
        }
        return "A request can't move from " + from + " to " + to + ".";
    }

    public void schedule(LocalDate date, String correlationId) {
        // Check before mutating: a refused move must leave the aggregate untouched.
        if (status == MaintenanceRequestStatus.CANCELLED || !status.canMoveTo(MaintenanceRequestStatus.SCHEDULED)) {
            throw new IllegalStateException(transitionRefusal(status, MaintenanceRequestStatus.SCHEDULED));
        }
        this.scheduledDate = date;
        changeStatus(MaintenanceRequestStatus.SCHEDULED, correlationId);
    }

    public void assignTo(String assignee, String correlationId) {
        if (status == MaintenanceRequestStatus.CANCELLED) {
            throw new IllegalStateException("This request was cancelled and can't be updated.");
        }
        this.assignedTo = assignee;
        if (this.status == MaintenanceRequestStatus.SUBMITTED) {
            changeStatus(MaintenanceRequestStatus.IN_REVIEW, correlationId);
        }
    }

    public static MaintenanceRequest rehydrate(
            UUID id,
            UUID tenantId,
            UUID unitId,
            UUID propertyId,
            UUID tenantProfileId,
            UUID leaseId,
            String title,
            String description,
            MaintenanceCategory category,
            MaintenancePriority priority,
            MaintenanceRequestStatus status,
            LocalDate scheduledDate,
            Instant completedAt,
            Instant firstLandlordResponseAt,
            Instant landlordViewedAt,
            String notes,
            String createdBy,
            String assignedTo,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        MaintenanceRequest request = MaintenanceRequest.builder()
                .unitId(unitId)
                .propertyId(propertyId)
                .tenantProfileId(tenantProfileId)
                .leaseId(leaseId)
                .title(title)
                .description(description)
                .category(category)
                .priority(priority)
                .status(status)
                .scheduledDate(scheduledDate)
                .completedAt(completedAt)
                .firstLandlordResponseAt(firstLandlordResponseAt)
                .landlordViewedAt(landlordViewedAt)
                .notes(notes)
                .createdBy(createdBy)
                .assignedTo(assignedTo)
                .build();

        request.setId(id);
        request.assignTenant(tenantId);
        request.setVersion(version);
        request.restoreCreatedAt(createdAt);
        request.restoreUpdatedAt(updatedAt);

        return request;
    }
}
