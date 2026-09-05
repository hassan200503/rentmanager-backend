package com.rentmanager.modules.property;

import com.rentmanager.modules.property.application.port.PublicVacancyPort;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The query behind "Available Properties" — whether a property is public
 * because it genuinely has somewhere to rent, not merely because it exists.
 *
 * <p>Before this port, the public listings page ran
 * {@code findByStatus(ACTIVE)}: a fully-occupied property was listed
 * identically to one with five vacancies, under a page heading that promised
 * "real vacancies, not stale listings" and a description a renter had no way
 * to verify without clicking through. Run against real PostgreSQL, like its
 * sibling {@code PublicRenterSearchIntegrationTest}, because the {@code EXISTS}
 * subquery and the {@code CAST(... AS string)} null-tolerance are exactly the
 * kind of JPQL-to-SQL translation a mock cannot verify.
 */
@Transactional
class PublicVacancyPortIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private PublicVacancyPort publicVacancyPort;

    @Autowired
    private EntityManager entityManager;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO tenants (id, version, created_at, updated_at, tenant_code, name,
                                     slug, email, phone_number, type, status)
                VALUES (:id, 0, NOW(), NOW(), :code, 'Vacancy Landlord',
                        :slug, 'vacancy@example.com', '+254700000001',
                        'INDIVIDUAL', 'ACTIVE')
                """)
                .setParameter("id", tenantId)
                .setParameter("code", "T-" + tenantId.toString().substring(0, 8))
                .setParameter("slug", "vacancy-" + tenantId.toString().substring(0, 8))
                .executeUpdate();
    }

    private UUID property(String name, String city, String state, PropertyType type) {
        UUID propertyId = UUID.randomUUID();
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
                .setParameter("name", name)
                .setParameter("type", type.name())
                .setParameter("premises", switch (type) {
                    case COMMERCIAL, OFFICE, WAREHOUSE -> "COMMERCIAL";
                    default -> "RESIDENTIAL";
                })
                .setParameter("city", city)
                .setParameter("state", state)
                .executeUpdate();
        entityManager.flush();
        return propertyId;
    }

    private void unit(UUID propertyId, String unitNumber, String rent, String occupancyStatus) {
        entityManager.createNativeQuery("""
                INSERT INTO units (id, version, created_at, updated_at, tenant_id, property_id,
                                   unit_number, label, status, occupancy_status,
                                   rent_amount, deposit_amount, description)
                VALUES (:id, 0, NOW(), NOW(), :tenantId, :propertyId, :unitNumber, :unitNumber,
                        'ACTIVE', :occupancyStatus, :rent, 0, 'x')
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenantId", tenantId)
                .setParameter("propertyId", propertyId)
                .setParameter("unitNumber", unitNumber)
                .setParameter("occupancyStatus", occupancyStatus)
                .setParameter("rent", new BigDecimal(rent))
                .executeUpdate();
        entityManager.flush();
    }

    private Page<UUID> search(String keyword, String location, String minRent, String maxRent,
                               PropertyType type) {
        return publicVacancyPort.findPropertyIdsWithVacancy(
                keyword, location,
                minRent == null ? null : new BigDecimal(minRent),
                maxRent == null ? null : new BigDecimal(maxRent),
                type,
                PageRequest.of(0, 50));
    }

    // ── The gap this closes ──────────────────────────────────────────────

    @Test
    void aFullyOccupiedPropertyIsExcludedFromPublicListings() {
        UUID occupied = property("Full House", "Kilimani", "Nairobi", PropertyType.APARTMENT);
        unit(occupied, "A1", "30000", "OCCUPIED");

        UUID available = property("Open House", "Kilimani", "Nairobi", PropertyType.APARTMENT);
        unit(available, "A1", "30000", "VACANT");

        Page<UUID> results = search(null, null, null, null, null);

        assertThat(results.getContent()).containsExactly(available);
    }

    @Test
    void aPropertyWithAtLeastOneVacantUnitIsIncludedEvenIfOthersAreOccupied() {
        UUID mixed = property("Mixed", "Kilimani", "Nairobi", PropertyType.APARTMENT);
        unit(mixed, "A1", "30000", "OCCUPIED");
        unit(mixed, "A2", "32000", "VACANT");

        assertThat(search(null, null, null, null, null).getContent()).containsExactly(mixed);
    }

    @Test
    void priceAndTypeFiltersApplyToTheAvailableUnitNotTheWholeProperty() {
        // A property whose only VACANT unit is outside the budget must not
        // appear just because a (currently occupied, and therefore
        // unrentable-today) unit would have matched.
        UUID property = property("Split Building", "Kisumu", "Kisumu", PropertyType.APARTMENT);
        unit(property, "Cheap-Taken", "10000", "OCCUPIED");
        unit(property, "Expensive-Free", "90000", "VACANT");

        assertThat(search(null, null, "5000", "20000", null).getContent()).isEmpty();
        assertThat(search(null, null, "80000", "100000", null).getContent()).containsExactly(property);
    }

    @Test
    void locationAndKeywordFiltersStillApply() {
        UUID nairobi = property("Green Court", "Kilimani", "Nairobi", PropertyType.APARTMENT);
        unit(nairobi, "A1", "30000", "VACANT");

        UUID kisumu = property("Lakeside", "Milimani", "Kisumu", PropertyType.APARTMENT);
        unit(kisumu, "A1", "18000", "VACANT");

        assertThat(search(null, "Nairobi", null, null, null).getContent()).containsExactly(nairobi);
        assertThat(search("Lakeside", null, null, null, null).getContent()).containsExactly(kisumu);
    }

    // ── Null tolerance: the documented bytea trap ────────────────────────

    @Test
    void browsingWithNoFiltersAtAllWorks() {
        UUID a = property("A", "Kisumu", "Kisumu", PropertyType.APARTMENT);
        unit(a, "A1", "20000", "VACANT");
        UUID b = property("B", "Nairobi", "Nairobi", PropertyType.APARTMENT);
        unit(b, "A1", "30000", "VACANT");

        assertThat(search(null, null, null, null, null).getContent()).containsExactlyInAnyOrder(a, b);
    }

    // ── summariseVacancy() ────────────────────────────────────────────────

    @Test
    void summariseVacancyReportsCountAndPriceRangeAcrossAvailableUnitsOnly() {
        UUID p = property("Range Test", "Kisumu", "Kisumu", PropertyType.APARTMENT);
        unit(p, "A1", "15000", "VACANT");
        unit(p, "A2", "22000", "VACANT");
        unit(p, "A3", "999999", "OCCUPIED"); // must not skew the range

        Map<UUID, PublicVacancyPort.VacancySummary> summary =
                publicVacancyPort.summariseVacancy(List.of(p));

        PublicVacancyPort.VacancySummary result = summary.get(p);
        assertThat(result.availableUnits()).isEqualTo(2);
        assertThat(result.minRent()).isEqualByComparingTo("15000");
        assertThat(result.maxRent()).isEqualByComparingTo("22000");
    }

    @Test
    void summariseVacancyOmitsAPropertyWithNothingAvailable() {
        UUID p = property("All Taken", "Kisumu", "Kisumu", PropertyType.APARTMENT);
        unit(p, "A1", "20000", "OCCUPIED");

        Map<UUID, PublicVacancyPort.VacancySummary> summary =
                publicVacancyPort.summariseVacancy(List.of(p));

        assertThat(summary).doesNotContainKey(p);
    }

    @Test
    void summariseVacancyToleratesAnEmptyIdList() {
        assertThat(publicVacancyPort.summariseVacancy(List.of())).isEmpty();
    }
}
