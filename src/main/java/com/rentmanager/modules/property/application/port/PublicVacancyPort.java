package com.rentmanager.modules.property.application.port;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the public listings page needs to know about vacancy, expressed as a
 * requirement of the property module and satisfied by the unit module.
 *
 * <h2>Why a port rather than a direct query</h2>
 * The dependency between these two modules runs one way: {@code unit} joins
 * {@code PropertyJpaEntity} in several queries, and {@code property} references
 * nothing in {@code unit}. Answering "which properties actually have somewhere
 * to rent, and at what price" requires unit data, so rather than reverse that
 * direction the property module declares the question here and the unit module
 * answers it.
 *
 * <h2>What this fixes</h2>
 * The public listings page previously listed every ACTIVE property, whether or
 * not a single unit in it was available, beneath a heading that read
 * "Available Properties" and a promise of "real vacancies, not stale listings".
 * A renter could browse a page of properties, click through each one, and find
 * nothing to rent in any of them. Availability is the entire reason the page
 * exists, so it belongs in the query, not in the copy.
 */
public interface PublicVacancyPort {

    /**
     * Ids of ACTIVE properties holding at least one publicly visible vacant
     * unit that satisfies the filters, ordered by property name so paging is
     * stable.
     *
     * <p>Rent bounds apply to the <em>unit</em>: a property qualifies when some
     * available unit falls inside the renter's budget, not when the property's
     * cheapest or dearest does.
     *
     * <p>Blank or null filters are no-ops. Null is accepted for every filter
     * because a renter browsing with nothing entered is the common case.
     */
    Page<UUID> findPropertyIdsWithVacancy(
            String keyword,
            String location,
            BigDecimal minRent,
            BigDecimal maxRent,
            PropertyType propertyType,
            Pageable pageable);

    /**
     * Availability and asking-price range per property, for the ids given.
     *
     * <p>One grouped query for the whole page rather than one per card. A
     * property with no publicly visible vacant unit is simply absent from the
     * result — callers must treat a missing key as "nothing available" rather
     * than substituting a zero that looks like a measurement.
     */
    Map<UUID, VacancySummary> summariseVacancy(List<UUID> propertyIds);

    /**
     * @param availableUnits how many units a renter could enquire about today
     * @param minRent        cheapest available unit's rent
     * @param maxRent        dearest available unit's rent
     */
    record VacancySummary(int availableUnits, BigDecimal minRent, BigDecimal maxRent) {
    }
}
