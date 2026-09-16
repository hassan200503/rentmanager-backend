package com.rentmanager.modules.maintenance.application.dto;

/**
 * SLA summary for the landlord Requests hub.
 *
 * <h2>What these numbers do and do not mean</h2>
 * {@code responseRatePct} is <b>punctuality among answered requests</b> —
 * of the requests that got a first response, how many arrived within 24
 * hours. It is NOT coverage. A landlord who answers two requests quickly and
 * ignores eight scores 100%.
 *
 * <p>That is a legitimate metric, but on its own it is a flattering one, and
 * it was previously the only one shown. {@code awaitingFirstResponse} and
 * {@code oldestAwaitingHours} exist so the hub can lead with the number that
 * actually requires action — how many renters are still waiting, and how long
 * the most neglected one has been waiting. Both count every unanswered
 * request, so neither can be improved by ignoring work.
 *
 * <p>{@code avgResponseHours} averages only answered requests, for the same
 * reason: an unanswered request has no response time to average. Read it
 * beside {@code awaitingFirstResponse}, never instead of it.
 *
 * <p>{@code responseRatePct} is null until {@code resolvedRequirementMet}
 * (at least {@code MIN_RESOLVED_REQUESTS_FOR_SLA} resolved): below that, a
 * rating derived from one or two requests is noise presented as a grade.
 */
public record MaintenanceSlaSummaryResponse(
        int totalRequests,
        int resolvedRequests,
        int respondedRequests,
        double avgResponseHours,
        boolean resolvedRequirementMet,
        Integer responseRatePct,

        /** Requests with no landlord response yet. Every one is a waiting renter. */
        int awaitingFirstResponse,

        /**
         * Hours the longest-waiting unanswered request has been open, or null
         * when nothing is waiting. This is the figure that should be
         * uncomfortable when it grows.
         */
        Long oldestAwaitingHours
) {
}
