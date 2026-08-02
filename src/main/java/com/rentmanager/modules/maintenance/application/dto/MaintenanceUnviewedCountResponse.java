package com.rentmanager.modules.maintenance.application.dto;

/**
 * V54: unviewed-requests badge support.
 * count is the number of maintenance requests the landlord has not seen yet
 * (landlord_viewed_at IS NULL). Both the read endpoint and the
 * mark-all-viewed endpoint return it, so the frontend can clear the badge
 * from the write response without a second round trip.
 */
public record MaintenanceUnviewedCountResponse(long count) {
}
