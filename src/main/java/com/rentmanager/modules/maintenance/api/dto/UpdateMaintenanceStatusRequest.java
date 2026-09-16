package com.rentmanager.modules.maintenance.api.dto;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A landlord's response to a maintenance request.
 *
 * <p>{@code note} is the reply the renter receives — it is included in their
 * SMS and shown in their portal. Optional, because a straightforward
 * transition ("completed") often needs no words, but it is the only way the
 * landlord can say anything at all, so it is not an afterthought.
 *
 * <p>Capped at 500 characters: it is delivered by SMS, and an unbounded field
 * would silently become several messages at the landlord's expense.
 */
public record UpdateMaintenanceStatusRequest(
        @NotNull MaintenanceRequestStatus status,
        @Size(max = 500, message = "Keep the message under 500 characters — it is sent to the renter by SMS")
        String note
) {}
