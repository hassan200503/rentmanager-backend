package com.rentmanager.modules.maintenance.application.dto;

/**
 * Phase 4a/5: SLA summary for the landlord Requests hub. Fields are only
 * meaningful once resolvedRequirementMet (>=5 resolved requests):
 * responseRatePct is null below that threshold - the landlord has not yet
 * produced enough resolved work for a response-time rating to be honest.
 */
public record MaintenanceSlaSummaryResponse(
        int totalRequests,
        int resolvedRequests,
        int respondedRequests,
        double avgResponseHours,
        boolean resolvedRequirementMet,
        Integer responseRatePct
) {
}
