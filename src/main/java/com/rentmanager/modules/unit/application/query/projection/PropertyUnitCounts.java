package com.rentmanager.modules.unit.application.query.projection;

import java.util.UUID;

/**
 * Occupied and total unit counts for one property, computed in SQL.
 *
 * <h2>Why this exists</h2>
 * {@code PropertyOccupancyRollupListener} already counts exactly these two
 * numbers whenever a unit changes — and then throws them away, persisting
 * only the derived {@link com.rentmanager.modules.property.domain.enums.OccupancyStatus}
 * enum. {@code Property.unitCount} is not a substitute: it lives on
 * {@code PropertyDimensions} and is a figure the landlord typed in when
 * creating the property, not a live count.
 *
 * <p>The consequence on the frontend was that no truthful per-property
 * occupancy percentage could be shown. The dashboard's "Top Properties" panel
 * filled the gap by deriving one from the sum of the property name's
 * character codes and ranking properties by it.
 *
 * <p>Read-side only: no schema change, no rollup to keep in sync, and the
 * numbers cannot drift from the units they count because they are counted at
 * query time.
 *
 * @param propertyId    the property these counts belong to
 * @param totalUnits    units on the property, excluding ARCHIVED
 * @param occupiedUnits of those, the ones whose occupancy status is OCCUPIED
 */
public record PropertyUnitCounts(
        UUID propertyId,
        long totalUnits,
        long occupiedUnits
) {

    /**
     * Occupancy as a whole percentage, or {@code null} when the property has
     * no units at all.
     *
     * <p>Null rather than zero: "no units yet" and "no units occupied" are
     * different facts about a property, and showing 0% for the first reads as
     * a problem where there is none.
     */
    public Integer occupancyPercent() {
        if (totalUnits <= 0) {
            return null;
        }
        return (int) Math.round((occupiedUnits * 100.0) / totalUnits);
    }
}
