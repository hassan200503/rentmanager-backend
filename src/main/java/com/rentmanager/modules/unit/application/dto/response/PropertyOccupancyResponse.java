package com.rentmanager.modules.unit.application.dto.response;

import java.util.UUID;

/**
 * Real occupancy for one property: how many of its units are occupied, out of
 * how many it has.
 *
 * <p>{@code occupancyPercent} is null when the property has no units yet —
 * distinct from 0%, which means it has units and none are let. Collapsing the
 * two would show a brand-new property as a problem.
 *
 * <p>Counts exclude ARCHIVED units, matching how
 * {@code PropertyOccupancyRollupListener} derives a property's
 * OccupancyStatus, so the number and the status can never disagree.
 */
public record PropertyOccupancyResponse(
        UUID propertyId,
        long totalUnits,
        long occupiedUnits,
        Integer occupancyPercent
) {}
