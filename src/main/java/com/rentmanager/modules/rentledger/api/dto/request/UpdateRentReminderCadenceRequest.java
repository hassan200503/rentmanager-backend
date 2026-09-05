package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The complete cadence, not a delta.
 *
 * <p>The settings screen shows all six milestones at once, so it submits all
 * six. Accepting a partial list would let the saved cadence differ from the
 * grid the landlord was looking at when they pressed save — and what is being
 * configured here is whether real people get contacted.
 */
public record UpdateRentReminderCadenceRequest(
        @NotEmpty(message = "At least one milestone must be supplied")
        @Valid
        List<Milestone> milestones
) {

    public record Milestone(
            @NotNull(message = "milestone is required")
            ReminderMilestone milestone,
            boolean enabled,
            boolean smsEnabled,
            boolean emailEnabled,
            boolean whatsappEnabled,
            boolean notifyLandlord
    ) {}
}
