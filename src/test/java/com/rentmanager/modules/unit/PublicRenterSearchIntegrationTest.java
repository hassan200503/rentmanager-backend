package com.rentmanager.modules.unit;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import com.rentmanager.modules.unit.application.query.service.PublicUnitQueryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The search a renter uses to find somewhere to live.
 *
 * <p>Run against real PostgreSQL rather than mocks for two reasons. The
 * filters are expressed in JPQL, so a mock proves nothing about whether they
 * actually select the right rows; and this file's predecessor query carried a
 * documented Postgres bug — a null bound into {@code LOWER(CONCAT(...))} makes
 * the driver infer {@code bytea} and fail with "function lower(bytea) does not
 * exist". That was found by an integration test once already, and every new
 * string filter is another chance to reintroduce it.
 *
 * <p>The failure mode being guarded against is quiet: a wrong filter does not
 * error, it silently hides listings a renter should have seen, and the
 * landlord paying for that listing never learns why nobody called.
 */
@Transactional
class PublicRenterSearchIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PublicUnitQueryService publicUnitQueryService;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        // Column names taken from the live schema, not guessed: the tenants
        // table uses `type`, not `tenant_type`.
        entityManager.createNativeQuery("""
                INSERT INTO tenants (id, version, created_at, updated_at, tenant_code, name,
                                     slug, email, phone_number, type, status)
                VALUES (:id, 0, NOW(), NOW(), :code, 'Search Landlord',
                        :slug, 'search@example.com', '+254700000000',
                        'INDIVIDUAL', 'ACTIVE')
                """)
                .setParameter("id", tenantId)
                .setParameter("code", "T-" + tenantId.toString().substring(0, 8))
                .setParameter("slug", "search-" + tenantId.toString().substring(0, 8))
                .executeUpdate();
    }

    /** A publicly visible vacant unit in a named city at a given rent. */
    private UUID listing(String propertyName, String city, String state,
                         PropertyType type, String rent, String description) {
        UUID propertyId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();

        entityManager.createNativeQuery("""
                INSERT INTO properties (id, version, created_at, updated_at, tenant_id,
                                        reference_code, name, description, status,
                                        property_type, premises_type, occupancy_status,
                                        city, state)
                VALUES (:id, 0, NOW(), NOW(), :tenantId, :ref, :name, 'desc', 'ACTIVE',
                        :type, :premises, 'VACANT', :city, :state)
                """)
                .setParameter("id", propertyId)
                .setParameter("tenantId", tenantId)
                .setParameter("ref", "P-" + propertyId.toString().substring(0, 8))
                .setParameter("name", propertyName)
                .setParameter("type", type.name())
                // premises_type is NOT NULL and is normally auto-derived from
                // property type at creation. COMMERCIAL/OFFICE/WAREHOUSE are
                // commercial premises; everything a renter would live in is
                // residential.
                .setParameter("premises", switch (type) {
                    case COMMERCIAL, OFFICE, WAREHOUSE -> "COMMERCIAL";
                    default -> "RESIDENTIAL";
                })
                .setParameter("city", city)
                .setParameter("state", state)
                .executeUpdate();

        entityManager.createNativeQuery("""
                INSERT INTO units (id, version, created_at, updated_at, tenant_id, property_id,
                                   unit_number, label, status, occupancy_status,
                                   rent_amount, deposit_amount, description)
                VALUES (:id, 0, NOW(), NOW(), :tenantId, :propertyId, 'A1', 'Unit A1',
                        'ACTIVE', 'VACANT', :rent, 0, :description)
                """)
                .setParameter("id", unitId)
                .setParameter("tenantId", tenantId)
                .setParameter("propertyId", propertyId)
                .setParameter("rent", new BigDecimal(rent))
                .setParameter("description", description)
                .executeUpdate();

        entityManager.flush();
        return unitId;
    }

    private long countMatching(String keyword, String city, String minRent, String maxRent,
                               PropertyType type) {
        return publicUnitQueryService.getVacantUnits(
                keyword,
                city,
                minRent == null ? null : new BigDecimal(minRent),
                maxRent == null ? null : new BigDecimal(maxRent),
                type,
                PageRequest.of(0, 50)
        ).getTotalElements();
    }

    // ── The gap this closes ──────────────────────────────────────────────

    /**
     * The whole point. Before this, searching a place name found nothing
     * unless a landlord had happened to type it into a free-text description.
     */
    @Test
    void aRenterCanSearchByWhereTheyWantToLive() {
        listing("Green Court", "Kilimani", "Nairobi", PropertyType.APARTMENT, "35000", "spacious");
        listing("Lakeside", "Kisumu", "Kisumu", PropertyType.APARTMENT, "18000", "spacious");

        assertThat(countMatching(null, "Kilimani", null, null, null)).isEqualTo(1);
        assertThat(countMatching(null, "Kisumu", null, null, null)).isEqualTo(1);
    }

    @Test
    void locationSearchAlsoMatchesTheCountyNotJustTheNeighbourhood() {
        listing("Green Court", "Kilimani", "Nairobi", PropertyType.APARTMENT, "35000", "x");

        assertThat(countMatching(null, "Nairobi", null, null, null)).isEqualTo(1);
    }

    @Test
    void locationSearchIsCaseInsensitiveAndPartial() {
        listing("Green Court", "Kilimani", "Nairobi", PropertyType.APARTMENT, "35000", "x");

        assertThat(countMatching(null, "kilim", null, null, null)).isEqualTo(1);
        assertThat(countMatching(null, "KILIMANI", null, null, null)).isEqualTo(1);
    }

    /** Budget is the second question every renter asks. */
    @Test
    void aRenterCanSearchWithinTheirBudget() {
        listing("Cheap", "Kisumu", "Kisumu", PropertyType.BEDSITTER, "8000", "x");
        listing("Mid", "Kisumu", "Kisumu", PropertyType.APARTMENT, "25000", "x");
        listing("Expensive", "Kisumu", "Kisumu", PropertyType.VILLA, "120000", "x");

        assertThat(countMatching(null, null, "10000", "50000", null)).isEqualTo(1);
        assertThat(countMatching(null, null, null, "10000", null)).isEqualTo(1);
        assertThat(countMatching(null, null, "100000", null, null)).isEqualTo(1);
    }

    @Test
    void theBudgetBoundsAreInclusive() {
        listing("Exact", "Kisumu", "Kisumu", PropertyType.APARTMENT, "25000", "x");

        assertThat(countMatching(null, null, "25000", "25000", null)).isEqualTo(1);
    }

    /**
     * There is no bedroom column, and in this market supply is described by
     * type — bedsitter, studio, apartment, maisonette — so that is what the
     * filter offers rather than a bedroom count nobody entered.
     */
    @Test
    void aRenterCanFilterByTheKindOfPlace() {
        listing("Bedsit Block", "Kisumu", "Kisumu", PropertyType.BEDSITTER, "8000", "x");
        listing("Family Home", "Kisumu", "Kisumu", PropertyType.MAISONETTE, "90000", "x");

        assertThat(countMatching(null, null, null, null, PropertyType.BEDSITTER)).isEqualTo(1);
        assertThat(countMatching(null, null, null, null, PropertyType.MAISONETTE)).isEqualTo(1);
        assertThat(countMatching(null, null, null, null, PropertyType.VILLA)).isZero();
    }

    @Test
    void filtersCombine() {
        listing("Right", "Kilimani", "Nairobi", PropertyType.APARTMENT, "35000", "x");
        listing("WrongCity", "Kisumu", "Kisumu", PropertyType.APARTMENT, "35000", "x");
        listing("WrongPrice", "Kilimani", "Nairobi", PropertyType.APARTMENT, "150000", "x");
        listing("WrongType", "Kilimani", "Nairobi", PropertyType.WAREHOUSE, "35000", "x");

        assertThat(countMatching(null, "Kilimani", "20000", "50000", PropertyType.APARTMENT))
                .isEqualTo(1);
    }

    // ── Keyword now reaches the fields a renter would actually type ──────

    @Test
    void keywordMatchesThePropertyNameAndPlaceNotJustTheUnitNumber() {
        listing("Green Court Apartments", "Kilimani", "Nairobi",
                PropertyType.APARTMENT, "35000", "x");

        assertThat(countMatching("Green Court", null, null, null, null)).isEqualTo(1);
        assertThat(countMatching("Kilimani", null, null, null, null)).isEqualTo(1);
        assertThat(countMatching("Nairobi", null, null, null, null)).isEqualTo(1);
    }

    // ── Null tolerance: the documented bytea trap ────────────────────────

    /**
     * A null keyword bound into {@code LOWER(CONCAT(...))} previously made the
     * Postgres driver infer `bytea` and fail. Every new string filter is
     * another chance to reintroduce it, so browsing with nothing set has to be
     * exercised explicitly.
     */
    @Test
    void browsingWithNoFiltersAtAllWorks() {
        listing("A", "Kisumu", "Kisumu", PropertyType.APARTMENT, "20000", "x");
        listing("B", "Nairobi", "Nairobi", PropertyType.APARTMENT, "30000", "x");

        assertThat(countMatching(null, null, null, null, null)).isEqualTo(2);
    }

    @Test
    void eachFilterIsIndependentlyNullTolerant() {
        listing("A", "Kisumu", "Kisumu", PropertyType.APARTMENT, "20000", "findme");

        assertThat(countMatching("findme", null, null, null, null)).isEqualTo(1);
        assertThat(countMatching(null, "Kisumu", null, null, null)).isEqualTo(1);
        assertThat(countMatching(null, null, "1", null, null)).isEqualTo(1);
        assertThat(countMatching(null, null, null, "999999", null)).isEqualTo(1);
        assertThat(countMatching(null, null, null, null, PropertyType.APARTMENT)).isEqualTo(1);
    }

    // ── Visibility rules must survive the new filters ────────────────────

    /**
     * The filters must narrow the publicly visible set, never widen it. An
     * occupied unit surfacing in search would send renters to a home that is
     * not available.
     */
    @Test
    void filtersNeverExposeAnOccupiedUnit() {
        UUID unitId = listing("Taken", "Kisumu", "Kisumu", PropertyType.APARTMENT, "20000", "x");
        entityManager.createNativeQuery(
                        "UPDATE units SET occupancy_status = 'OCCUPIED' WHERE id = :id")
                .setParameter("id", unitId)
                .executeUpdate();
        entityManager.flush();

        assertThat(countMatching(null, "Kisumu", null, null, null)).isZero();
        assertThat(countMatching(null, null, null, null, null)).isZero();
    }

    @Test
    void filtersNeverExposeAUnitWhosePropertyIsInactive() {
        listing("Hidden", "Kisumu", "Kisumu", PropertyType.APARTMENT, "20000", "x");
        entityManager.createNativeQuery("UPDATE properties SET status = 'DRAFT'").executeUpdate();
        entityManager.flush();

        assertThat(countMatching(null, "Kisumu", null, null, null)).isZero();
    }
}
