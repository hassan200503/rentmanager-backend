package com.rentmanager.modules.unit;

import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import com.rentmanager.modules.unit.application.query.projection.PropertyUnitCounts;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the per-property occupancy counts are computed from real units.
 *
 * <p>This closes the gap that let the dashboard invent occupancy. A truthful
 * per-property percentage needs occupied-over-total for that property; those
 * numbers were computed by {@code PropertyOccupancyRollupListener} and thrown
 * away, and {@code Property.unitCount} is a dimension the landlord typed in.
 * With nothing real to show, "Top Properties" derived a percentage from the
 * sum of the property name's character codes and ranked by it.
 *
 * <p>The ARCHIVED exclusion is the assertion that matters most: the rollup
 * listener excludes archived units when deriving a property's
 * {@code OccupancyStatus}, so if this query counted them a property could
 * read "fully occupied" while its own numbers disagreed.
 */
@Transactional
class PropertyOccupancyCountsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;
    private UUID propertyId;

    @BeforeEach
    void setUp() {
        MinimalTenantChainFixture.Chain chain =
                MinimalTenantChainFixture.persistFullChain(entityManager);
        tenantId = chain.tenantId();
        propertyId = chain.propertyId();

        // persistFullChain() creates one unit as part of the chain. These
        // tests assert exact counts, so each one starts from an empty
        // property and inserts precisely the units it means to count.
        entityManager.createNativeQuery("DELETE FROM units WHERE property_id = :p")
                .setParameter("p", propertyId)
                .executeUpdate();
        entityManager.flush();
    }

    /**
     * Inserts a unit directly. The fixture's own unit-creation path carries
     * more machinery than this needs, and the point here is what the grouped
     * query counts, not how a unit is created.
     */
    private void insertUnit(String unitNumber, String status, String occupancy) {
        entityManager.createNativeQuery(
                        "INSERT INTO units (id, tenant_id, property_id, unit_number, status, "
                                + "occupancy_status, created_at, updated_at, version) "
                                + "VALUES (:id, :t, :p, :num, :status, :occ, NOW(), NOW(), 0)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("t", tenantId)
                .setParameter("p", propertyId)
                .setParameter("num", unitNumber)
                .setParameter("status", status)
                .setParameter("occ", occupancy)
                .executeUpdate();
    }

    private PropertyUnitCounts countsForProperty() {
        List<PropertyUnitCounts> all = unitRepository.countUnitsByProperty(tenantId);
        return all.stream()
                .filter(c -> c.propertyId().equals(propertyId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no counts returned for the property"));
    }

    @Test
    void countsOccupiedAgainstTotal() {
        insertUnit("A1", "ACTIVE", "OCCUPIED");
        insertUnit("A2", "ACTIVE", "OCCUPIED");
        insertUnit("A3", "ACTIVE", "VACANT");
        entityManager.flush();
        entityManager.clear();

        PropertyUnitCounts counts = countsForProperty();

        assertThat(counts.totalUnits()).isEqualTo(3);
        assertThat(counts.occupiedUnits()).isEqualTo(2);
        assertThat(counts.occupancyPercent()).isEqualTo(67);
    }

    /**
     * The rollup listener excludes ARCHIVED when deriving OccupancyStatus. If
     * this query included them, a property could show "fully occupied" beside
     * a count that said otherwise.
     */
    @Test
    void archivedUnitsAreExcludedFromBothFigures() {
        insertUnit("B1", "ACTIVE", "OCCUPIED");
        insertUnit("B2", "ARCHIVED", "OCCUPIED");
        insertUnit("B3", "ARCHIVED", "VACANT");
        entityManager.flush();
        entityManager.clear();

        PropertyUnitCounts counts = countsForProperty();

        assertThat(counts.totalUnits()).isEqualTo(1);
        assertThat(counts.occupiedUnits()).isEqualTo(1);
        assertThat(counts.occupancyPercent()).isEqualTo(100);
    }

    /**
     * RESERVED and PENDING_PAYMENT are real occupancy states but they are not
     * OCCUPIED — rent is not being collected on them yet.
     */
    @Test
    void onlyOccupiedCountsAsOccupied() {
        insertUnit("C1", "ACTIVE", "OCCUPIED");
        insertUnit("C2", "ACTIVE", "RESERVED");
        insertUnit("C3", "ACTIVE", "PENDING_PAYMENT");
        insertUnit("C4", "ACTIVE", "VACANT");
        entityManager.flush();
        entityManager.clear();

        PropertyUnitCounts counts = countsForProperty();

        assertThat(counts.totalUnits()).isEqualTo(4);
        assertThat(counts.occupiedUnits()).isEqualTo(1);
        assertThat(counts.occupancyPercent()).isEqualTo(25);
    }

    @Test
    void aPropertyWithNoUnitsReportsNullRatherThanZeroPercent() {
        entityManager.flush();
        entityManager.clear();

        // No units inserted, so the property does not appear in a GROUP BY
        // over units at all — which the caller must treat as "unknown", not
        // as 0%. The projection makes the same distinction for a row that
        // does exist with zero units.
        List<PropertyUnitCounts> all = unitRepository.countUnitsByProperty(tenantId);

        assertThat(all).noneMatch(c -> c.propertyId().equals(propertyId));
        assertThat(new PropertyUnitCounts(propertyId, 0, 0).occupancyPercent()).isNull();
    }

    @Test
    void aFullyVacantPropertyReportsZeroNotNull() {
        insertUnit("D1", "ACTIVE", "VACANT");
        insertUnit("D2", "ACTIVE", "VACANT");
        entityManager.flush();
        entityManager.clear();

        PropertyUnitCounts counts = countsForProperty();

        assertThat(counts.occupancyPercent())
                .as("has units, none let — that is 0%, distinct from 'no units yet'")
                .isEqualTo(0);
    }

    /**
     * Cross-tenant isolation. The query is grouped by property but filtered
     * by landlord, and a landlord must never see another's unit counts.
     */
    @Test
    void anotherLandlordsUnitsAreNotCounted() {
        insertUnit("E1", "ACTIVE", "OCCUPIED");

        // The other landlord keeps the unit its own fixture chain created,
        // plus this one — either way none of them may appear in my counts.
        MinimalTenantChainFixture.Chain other =
                MinimalTenantChainFixture.persistFullChain(entityManager);
        entityManager.createNativeQuery(
                        "INSERT INTO units (id, tenant_id, property_id, unit_number, status, "
                                + "occupancy_status, created_at, updated_at, version) "
                                + "VALUES (:id, :t, :p, 'X1', 'ACTIVE', 'OCCUPIED', NOW(), NOW(), 0)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("t", other.tenantId())
                .setParameter("p", other.propertyId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<PropertyUnitCounts> mine = unitRepository.countUnitsByProperty(tenantId);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).propertyId()).isEqualTo(propertyId);
        assertThat(mine).noneMatch(c -> c.propertyId().equals(other.propertyId()));
    }
}
