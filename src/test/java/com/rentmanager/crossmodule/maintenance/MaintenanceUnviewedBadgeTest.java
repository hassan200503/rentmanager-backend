package com.rentmanager.crossmodule.maintenance;

import com.rentmanager.crossmodule.core.CrossModuleBaseIT;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestQueryService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.support.MinimalTenantChainFixture;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sidebar badge: opening the Requests hub means the landlord has seen
 * what is in it, so the count of unseen requests drops to zero.
 *
 * <p>This existed and was broken in a way no unit test could see. The bulk
 * UPDATE behind it bound one LocalDateTime to two parameters, and updated_at
 * is an Instant inherited from BaseEntity - so Hibernate rejected the
 * statement at bind time and POST /maintenance/read returned 500 every
 * single time, having touched no rows. It compiled, and every mock-based
 * test of the layers around it passed, because the only thing that can
 * catch a parameter-binding failure is running the query against a real
 * database.
 *
 * <p>So these tests go through the command service to Postgres deliberately.
 * A test that mocked the repository here would assert nothing at all.
 */
class MaintenanceUnviewedBadgeTest extends CrossModuleBaseIT {

    @Autowired
    private MaintenanceRequestCommandService commandService;

    @Autowired
    private MaintenanceRequestQueryService queryService;

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @Autowired
    private EntityManager entityManager;

    private record Fixture(UUID tenantId, UUID propertyId, UUID unitId, UUID profileId) {}

    private Fixture landlordWithATenantedUnit() {
        MinimalTenantChainFixture.Chain chain =
                MinimalTenantChainFixture.persistFullChain(entityManager);
        return new Fixture(chain.tenantId(), chain.propertyId(), chain.unitId(),
                chain.tenantProfileId());
    }

    private void report(Fixture f, String title) {
        tenantExecutor.executeAsTenant(f.tenantId(), () ->
                commandService.submit(
                        f.tenantId(), f.unitId(), f.propertyId(), f.profileId(), null,
                        title, "Reported by the renter.",
                        MaintenanceCategory.PLUMBING, MaintenancePriority.MEDIUM,
                        "renter", UUID.randomUUID().toString()));
    }

    private long unseen(Fixture f) {
        return tenantExecutor.executeAsTenant(f.tenantId(),
                () -> queryService.countUnviewed(f.tenantId()));
    }

    private int openTheHub(Fixture f) {
        return tenantExecutor.executeAsTenant(f.tenantId(),
                () -> commandService.markAllViewed(f.tenantId()));
    }

    @Test
    void openingTheHubClearsTheBadge() {
        Fixture f = landlordWithATenantedUnit();
        report(f, "Leaking tap");
        report(f, "Broken window");

        assertThat(unseen(f))
                .as("two requests nobody has opened is a badge showing two")
                .isEqualTo(2);

        assertThat(openTheHub(f))
                .as("the statement must reach the rows, not fail at bind time")
                .isEqualTo(2);

        assertThat(unseen(f))
                .as("having looked at them, the landlord should not still be nagged")
                .isZero();
    }

    /**
     * The hub re-runs this whenever the unseen count changes, so a second
     * call with nothing left to mark is a normal event, not an error. It
     * must report zero rather than re-stamping requests that were already
     * seen - the timestamp is evidence of when the landlord first looked.
     */
    @Test
    void openingItAgainMarksNothingAndStampsNothing() {
        Fixture f = landlordWithATenantedUnit();
        report(f, "Leaking tap");
        openTheHub(f);

        assertThat(openTheHub(f)).isZero();
        assertThat(unseen(f)).isZero();
    }

    /** A request that arrives after the landlord looked is genuinely unseen. */
    @Test
    void aRequestFiledAfterwardsCountsAgain() {
        Fixture f = landlordWithATenantedUnit();
        report(f, "Leaking tap");
        openTheHub(f);

        report(f, "No water since Friday");

        assertThat(unseen(f))
                .as("the badge exists to surface what is new")
                .isEqualTo(1);
        assertThat(openTheHub(f)).isEqualTo(1);
    }
}
