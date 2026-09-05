package com.rentmanager.modules.unit.infrastructure.adapter;

import com.rentmanager.modules.property.application.port.PublicVacancyPort;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The unit module's answer to the property module's vacancy question.
 *
 * <p>Lives here, not in {@code property}, because it is unit data. The two
 * modules already depend one way — {@code unit} joins {@code PropertyJpaEntity},
 * {@code property} knows nothing of units — and implementing the port on this
 * side keeps that direction intact.
 *
 * <h2>Blank is null, and null means "no filter"</h2>
 * The frontend sends {@code ?keyword=} for an empty search box. Left as an
 * empty string that would become {@code LIKE '%%'}, which happens to match
 * everything and so looks harmless, but the same empty string in a future
 * exact-match filter would match nothing. Normalising to null once, here,
 * makes "the renter did not fill this in" a single unambiguous value.
 */
@Component
@RequiredArgsConstructor
public class PublicVacancyAdapter implements PublicVacancyPort {

    private final UnitJpaRepository unitJpaRepository;

    /** Null unless the renter actually typed something. */
    private static String filterOrNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    @Override
    public Page<UUID> findPropertyIdsWithVacancy(
            String keyword,
            String location,
            BigDecimal minRent,
            BigDecimal maxRent,
            PropertyType propertyType,
            Pageable pageable) {

        return unitJpaRepository.searchPropertyIdsWithVacancy(
                filterOrNull(keyword),
                filterOrNull(location),
                minRent,
                maxRent,
                propertyType,
                UnitOccupancyStatus.VACANT,
                UnitStatus.ACTIVE,
                PropertyStatus.ACTIVE,
                pageable);
    }

    @Override
    public Map<UUID, VacancySummary> summariseVacancy(List<UUID> propertyIds) {
        // An IN () with no values is a syntax error on Postgres, and there is
        // nothing to summarise anyway.
        if (propertyIds == null || propertyIds.isEmpty()) {
            return Map.of();
        }

        return unitJpaRepository.summariseVacancy(
                        propertyIds,
                        UnitOccupancyStatus.VACANT,
                        UnitStatus.ACTIVE)
                .stream()
                .collect(Collectors.toMap(
                        UnitJpaRepository.VacancySummaryRow::getPropertyId,
                        row -> new VacancySummary(
                                // The count is a COUNT(*) over one property's
                                // vacant units; it cannot realistically exceed
                                // int range, and the DTO reads better as an int.
                                Math.toIntExact(row.getAvailableUnits()),
                                row.getMinRent(),
                                row.getMaxRent()),
                        // GROUP BY propertyId cannot produce a duplicate key,
                        // but toMap without a merge function throws rather than
                        // says so; keeping the first is the harmless answer.
                        (first, second) -> first,
                        java.util.LinkedHashMap::new));
    }
}
