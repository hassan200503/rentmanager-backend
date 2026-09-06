package com.rentmanager.modules.maintenance.domain.model;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what it means for a landlord to respond to a maintenance request.
 *
 * <p>Before this, responding meant changing an enum. A renter who reported a
 * locked water tank received "Update on Maintenance Request: in review" and
 * nothing else — it could not tell them which of their requests had moved,
 * what was happening, or when. These tests pin the two things that changed:
 * a landlord can now say something, and that something reaches the renter.
 */
class LandlordResponseTest {

    private MaintenanceRequest submitted() {
        return MaintenanceRequest.submit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Water tank locked",
                "The tank on the roof has been padlocked since Friday.",
                MaintenanceCategory.PLUMBING, MaintenancePriority.HIGH,
                "renter", "corr-1");
    }

    private MaintenanceRequestStatusChanged lastStatusEvent(MaintenanceRequest request) {
        return request.pullDomainEvents().stream()
                .filter(MaintenanceRequestStatusChanged.class::isInstance)
                .map(MaintenanceRequestStatusChanged.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    @Test
    void theReplyIsStoredAndTravelsToTheRenterWithTheRequestTitle() {
        MaintenanceRequest request = submitted();
        request.pullDomainEvents();

        request.changeStatus(MaintenanceRequestStatus.SCHEDULED,
                "Plumber booked for Thursday morning, he has the key.", "corr-2");

        assertThat(request.getNotes()).isEqualTo("Plumber booked for Thursday morning, he has the key.");

        MaintenanceRequestStatusChanged event = lastStatusEvent(request);
        assertThat(event.getLandlordNote())
                .as("the note must reach the notification listener, or the renter still learns nothing")
                .isEqualTo("Plumber booked for Thursday morning, he has the key.");
        assertThat(event.getTitle())
                .as("without the title the SMS cannot say WHICH request moved")
                .isEqualTo("Water tank locked");
    }

    /**
     * A note with no status change is a legitimate response — "still waiting
     * on the part" is exactly the update a renter wants. Rejecting it would
     * push landlords into making meaningless status moves in order to be able
     * to say anything at all.
     */
    @Test
    void aMessageAloneCountsAsAResponse() {
        MaintenanceRequest request = submitted();
        request.pullDomainEvents();

        request.changeStatus(MaintenanceRequestStatus.SUBMITTED,
                "Still sourcing the replacement padlock — sorry for the wait.", "corr-3");

        assertThat(request.getFirstLandlordResponseAt()).isNotNull();
        assertThat(request.getNotes()).contains("Still sourcing");
        assertThat(lastStatusEvent(request).getLandlordNote()).contains("Still sourcing");
    }

    /** A no-op stays a no-op: same status, nothing said, nothing recorded. */
    @Test
    void anEmptyChangeIsStillIgnored() {
        MaintenanceRequest request = submitted();
        request.pullDomainEvents();

        request.changeStatus(MaintenanceRequestStatus.SUBMITTED, "   ", "corr-4");

        assertThat(request.getFirstLandlordResponseAt())
                .as("saying nothing and changing nothing is not a response")
                .isNull();
        assertThat(request.hasDomainEvents()).isFalse();
    }

    /**
     * The SLA-gaming case. Cancelling a request is closing it, not answering
     * it — and while it counted as a first response, a landlord could improve
     * their response-time rating by discarding the requests they had ignored.
     * That is precisely backwards for a metric meant to protect renters.
     */
    @Test
    void cancellingIsNotAResponse() {
        MaintenanceRequest request = submitted();
        request.pullDomainEvents();

        request.changeStatus(MaintenanceRequestStatus.CANCELLED, null, "corr-5");

        assertThat(request.getFirstLandlordResponseAt())
                .as("closing a request must never earn credit for answering it")
                .isNull();
        assertThat(request.getStatus()).isEqualTo(MaintenanceRequestStatus.CANCELLED);
    }

    @Test
    void theFirstResponseTimeIsStampedOnceAndNeverMovedBySubsequentReplies() {
        MaintenanceRequest request = submitted();
        request.changeStatus(MaintenanceRequestStatus.IN_REVIEW, "Looking into it.", "corr-6");

        var firstResponse = request.getFirstLandlordResponseAt();
        assertThat(firstResponse).isNotNull();

        request.changeStatus(MaintenanceRequestStatus.COMPLETED, "Fixed and tested.", "corr-7");

        assertThat(request.getFirstLandlordResponseAt())
                .as("it measures time to FIRST reply; a later one must not reset it")
                .isEqualTo(firstResponse);
        assertThat(request.getNotes())
                .as("the latest reply is what the renter should see")
                .isEqualTo("Fixed and tested.");
    }
}
