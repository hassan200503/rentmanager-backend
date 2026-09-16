package com.rentmanager.modules.lease.application.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public class LeaseActionRequest {

    /**
     * Ignored. Who performed an action is taken from the authenticated user
     * (LeaseController sets actor). Kept only so existing clients that still
     * send it are not rejected.
     */
    private UUID performedBy;

    @NotNull(message = "action is required")
    private LeaseActionType action;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate actionDate;

    /**
     * Required only when action = TERMINATE
     */
    private TerminationType terminationType;

    private String reason;

    private String actor;

    public LeaseActionRequest() {}

    public UUID getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(UUID performedBy) {
        this.performedBy = performedBy;
    }

    public LeaseActionType getAction() {
        return action;
    }

    public void setAction(LeaseActionType action) {
        this.action = action;
    }

    public LocalDate getActionDate() {
        return actionDate;
    }

    public void setActionDate(LocalDate actionDate) {
        this.actionDate = actionDate;
    }

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public void setTerminationType(TerminationType terminationType) {
        this.terminationType = terminationType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    @AssertTrue(message = "reason is required for REJECT or TERMINATE actions")
    public boolean isReasonValid() {

        if (action == null) return true;

        return switch (action) {
            case REJECT, TERMINATE ->
                    reason != null && !reason.trim().isEmpty();
            default -> true;
        };
    }

    @AssertTrue(message = "terminationType is required for TERMINATE action")
    public boolean isTerminationValid() {

        if (action == null) return true;

        return switch (action) {
            case TERMINATE -> terminationType != null;
            default -> true;
        };
    }
}