package com.rentmanager.modules.maintenance.domain.model;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.UUID;

import static com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TD-132: status moves that would tell a renter something false are refused. */
class MaintenanceStatusTransitionTest {

    private MaintenanceRequest request() {
        return MaintenanceRequest.submit(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Leaking tap", "Kitchen", MaintenanceCategory.PLUMBING, MaintenancePriority.MEDIUM,
                "renter@test", "corr-1");
    }

    private MaintenanceRequest inStatus(MaintenanceRequestStatus status) {
        MaintenanceRequest r = request();
        switch (status) {
            case SUBMITTED -> { }
            case IN_REVIEW, SCHEDULED, IN_PROGRESS, CANCELLED -> r.changeStatus(status, "c");
            case COMPLETED -> r.changeStatus(COMPLETED, "c");
        }
        return r;
    }

    @ParameterizedTest
    @EnumSource(value = MaintenanceRequestStatus.class, names = {"IN_REVIEW", "SCHEDULED", "IN_PROGRESS", "COMPLETED"})
    void nothingMovesBackToSubmitted(MaintenanceRequestStatus from) {
        MaintenanceRequest r = inStatus(from);
        assertThatThrownBy(() -> r.changeStatus(SUBMITTED, "c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("back to Submitted");
        assertThat(r.getStatus()).isEqualTo(from);
    }

    @ParameterizedTest
    @EnumSource(MaintenanceRequestStatus.class)
    void cancelledIsTerminalEvenForANote(MaintenanceRequestStatus to) {
        MaintenanceRequest r = inStatus(CANCELLED);
        assertThatThrownBy(() -> r.changeStatus(to, "a note", "c")).isInstanceOf(IllegalStateException.class);
        assertThat(r.getStatus()).isEqualTo(CANCELLED);
    }

    @Test
    void completedCanOnlyBeReopenedAsInProgress() {
        MaintenanceRequest r = inStatus(COMPLETED);
        assertThatThrownBy(() -> r.changeStatus(SCHEDULED, "c")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> r.changeStatus(CANCELLED, "c")).isInstanceOf(IllegalStateException.class);
        r.changeStatus(IN_PROGRESS, "Leak came back", "c");
        assertThat(r.getStatus()).isEqualTo(IN_PROGRESS);
    }

    @Test
    void workingStatesMoveFreelyAndCanBeRescheduled() {
        MaintenanceRequest r = request();
        r.changeStatus(IN_PROGRESS, "c");
        r.changeStatus(SCHEDULED, "Plumber moved to Friday", "c");
        r.changeStatus(IN_REVIEW, "c");
        r.changeStatus(COMPLETED, "c");
        assertThat(r.getStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void aNoteOnTheSameStatusIsStillAReply() {
        MaintenanceRequest r = inStatus(SCHEDULED);
        r.changeStatus(SCHEDULED, "Still on for Friday", "c");
        assertThat(r.getNotes()).isEqualTo("Still on for Friday");
    }

    @Test
    void refusedScheduleLeavesTheDateUntouched() {
        MaintenanceRequest r = inStatus(COMPLETED);
        LocalDate before = r.getScheduledDate();
        assertThatThrownBy(() -> r.schedule(LocalDate.now().plusDays(3), "c")).isInstanceOf(IllegalStateException.class);
        assertThat(r.getScheduledDate()).isEqualTo(before);
    }

    @Test
    void cancelledRequestCannotBeAssigned() {
        MaintenanceRequest r = inStatus(CANCELLED);
        assertThatThrownBy(() -> r.assignTo("fixer@x", "c")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowedNextNeverOffersSubmittedOrLeavesCancelled() {
        for (MaintenanceRequestStatus s : MaintenanceRequestStatus.values()) {
            assertThat(s.allowedNext()).doesNotContain(SUBMITTED);
        }
        assertThat(CANCELLED.allowedNext()).isEmpty();
        assertThat(COMPLETED.allowedNext()).containsExactly(IN_PROGRESS);
    }
}
