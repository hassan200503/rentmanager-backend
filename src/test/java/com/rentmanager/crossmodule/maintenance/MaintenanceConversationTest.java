package com.rentmanager.crossmodule.maintenance;

import com.rentmanager.crossmodule.core.CrossModuleBaseIT;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestQueryService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end: a renter reports a problem, the landlord answers it, and the
 * renter can read the answer.
 *
 * <p>Every part of this loop was verified in isolation by unit tests, and the
 * loop itself never was — which is how the feature reached production with no
 * way for a landlord to say anything at all. The unit tests all passed because
 * each one asserted the behaviour of a single step; nothing asked whether the
 * steps joined up into something a person could use.
 *
 * <p>Runs against a real Postgres through the same services the controllers
 * call, so the persistence mapping is exercised too: the reply has to survive
 * a round trip to the database, not merely exist on an in-memory aggregate.
 */
class MaintenanceConversationTest extends CrossModuleBaseIT {

    @Autowired
    private MaintenanceRequestCommandService commandService;

    @Autowired
    private MaintenanceRequestQueryService queryService;

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @Autowired
    private EntityManager entityManager;

    private record Fixture(UUID tenantId, UUID propertyId, UUID unitId, UUID profileId) {}

    /**
     * maintenance_requests carries real foreign keys to tenants, properties,
     * units and tenant_profile (V72-V75), so a random UUID is not a landlord.
     * MinimalTenantChainFixture persists the whole chain by raw SQL, which is
     * what the other integration tests in this repo use for the same reason.
     */
    private Fixture landlordWithATenantedUnit() {
        MinimalTenantChainFixture.Chain chain =
                MinimalTenantChainFixture.persistFullChain(entityManager);
        return new Fixture(chain.tenantId(), chain.propertyId(), chain.unitId(),
                chain.tenantProfileId());
    }

    private MaintenanceRequest report(Fixture f, String title, MaintenancePriority priority) {
        return tenantExecutor.executeAsTenant(f.tenantId(), () ->
                commandService.submit(
                        f.tenantId(), f.unitId(), f.propertyId(), f.profileId(), null,
                        title, "The tank on the roof has been padlocked since Friday.",
                        MaintenanceCategory.PLUMBING, priority, "renter", UUID.randomUUID().toString()));
    }

    @Test
    void aRenterReportsAProblemAndCanReadTheLandlordsAnswer() {
        Fixture f = landlordWithATenantedUnit();

        MaintenanceRequest reported = report(f, "Water tank locked", MaintenancePriority.HIGH);

        // Nobody has answered yet, and the SLA must say so rather than
        // reporting a flattering silence.
        var beforeReply = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.getSlaSummary(f.tenantId()));
        assertThat(beforeReply.awaitingFirstResponse())
                .as("a reported problem nobody has answered is a renter waiting")
                .isEqualTo(1);
        assertThat(beforeReply.respondedRequests()).isZero();

        // The landlord answers, in words.
        tenantExecutor.executeAsTenant(f.tenantId(), () ->
                commandService.updateStatus(
                        f.tenantId(), reported.getId(),
                        MaintenanceRequestStatus.SCHEDULED,
                        "Plumber booked for Thursday morning, he has the key.",
                        UUID.randomUUID().toString()));

        // What the renter sees when they open the request. Read back through
        // the query service so this proves the reply survived persistence,
        // not just that the setter ran.
        MaintenanceRequest afterReply = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.findByIdAndTenantId(reported.getId(), f.tenantId()));

        assertThat(afterReply.getNotes())
                .as("the landlord's words are what the renter came for")
                .isEqualTo("Plumber booked for Thursday morning, he has the key.");
        assertThat(afterReply.getStatus()).isEqualTo(MaintenanceRequestStatus.SCHEDULED);
        assertThat(afterReply.getFirstLandlordResponseAt()).isNotNull();

        var afterSla = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.getSlaSummary(f.tenantId()));
        assertThat(afterSla.awaitingFirstResponse())
                .as("answering someone must remove them from the waiting count")
                .isZero();
        assertThat(afterSla.respondedRequests()).isEqualTo(1);
        assertThat(afterSla.oldestAwaitingHours())
                .as("nobody waiting means null, not zero hours")
                .isNull();
    }

    /**
     * The metric this feature exists to keep honest. A landlord who answers
     * one request quickly and ignores another must not be able to look
     * perfect — which is exactly what the hub reported before
     * awaitingFirstResponse existed.
     */
    @Test
    void answeringOneRequestDoesNotHideAnotherThatWasIgnored() {
        Fixture f = landlordWithATenantedUnit();

        MaintenanceRequest answered = report(f, "Leaking taps", MaintenancePriority.MEDIUM);
        report(f, "No water since Friday", MaintenancePriority.URGENT);

        tenantExecutor.executeAsTenant(f.tenantId(), () ->
                commandService.updateStatus(
                        f.tenantId(), answered.getId(),
                        MaintenanceRequestStatus.COMPLETED, "Washer replaced.",
                        UUID.randomUUID().toString()));

        var sla = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.getSlaSummary(f.tenantId()));

        assertThat(sla.respondedRequests()).isEqualTo(1);
        assertThat(sla.awaitingFirstResponse())
                .as("the urgent one nobody touched still counts, and must")
                .isEqualTo(1);
        assertThat(sla.oldestAwaitingHours()).isNotNull();
    }

    /**
     * Closing a request is not answering it. While cancelling stamped a first
     * response, a landlord could improve their rating by discarding the work
     * they had ignored.
     */
    @Test
    void cancellingARequestDoesNotCountAsAnsweringIt() {
        Fixture f = landlordWithATenantedUnit();
        MaintenanceRequest reported = report(f, "Broken window", MaintenancePriority.LOW);

        tenantExecutor.executeAsTenant(f.tenantId(), () ->
                commandService.updateStatus(
                        f.tenantId(), reported.getId(),
                        MaintenanceRequestStatus.CANCELLED, null,
                        UUID.randomUUID().toString()));

        MaintenanceRequest after = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.findByIdAndTenantId(reported.getId(), f.tenantId()));
        assertThat(after.getFirstLandlordResponseAt()).isNull();

        var sla = tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.getSlaSummary(f.tenantId()));
        assertThat(sla.respondedRequests()).isZero();
        assertThat(sla.awaitingFirstResponse())
                .as("a cancelled request is closed, so nobody is left waiting on it")
                .isZero();
    }
}
