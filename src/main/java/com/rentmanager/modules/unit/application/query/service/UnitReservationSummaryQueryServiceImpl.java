package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.application.dto.response.UnitReservationSummaryResponse;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitReservationSummaryQueryServiceImpl implements UnitReservationSummaryQueryService {

    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

    private static final int DEPOSIT_MONTHS = 2;

    @Override
    public UnitReservationSummaryResponse getSummary(UUID unitId) {

        // PUBLIC LISTING HARDENING (2026-07-08, Phase 2):
        // This is an unauthenticated, public endpoint. Previously used
        // unitRepository.findById(unitId), an unscoped tenant-agnostic
        // lookup with no status filtering at all — meaning a caller with
        // any unit UUID (guessed or leaked) could pull reservation-summary
        // data for a unit that was DRAFT/INACTIVE/ARCHIVED, or belonged to
        // a non-ACTIVE property, without ever appearing on a public listing
        // page. That mirrors the exact vulnerability class fixed in Phase 1
        // for the listing endpoints.
        //
        // Fixed by routing through findPubliclyVisibleVacantUnitById, which
        // already enforces the same defense-in-depth check established in
        // Phase 1: UnitStatus.ACTIVE + UnitOccupancyStatus.VACANT + parent
        // PropertyStatus.ACTIVE, all in one query. A unit that doesn't
        // qualify throws ResourceNotFoundException here exactly as before,
        // preserving the existing 404-not-403 behavior.
        //
        // Scope decision (confirmed, not assumed): this fix intentionally
        // does NOT allow the summary to keep resolving once a unit moves to
        // PENDING_PAYMENT/RESERVED occupancy during an in-progress
        // reservation attempt. That is a separate frontend/UX question
        // (does the reservation form need to keep working off cached data
        // through a slow M-Pesa STK-push confirmation window rather than
        // re-fetching /summary?) and is being flagged to the frontend
        // investigation (handoff §4.1), not decided here.
        Unit unit = unitRepository.findPubliclyVisibleVacantUnitById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit not found", ErrorCode.UNIT_NOT_FOUND
                ));

        // Property is already guaranteed ACTIVE by the join inside
        // findPubliclyVisibleVacantUnitById above. This second lookup is
        // purely to fetch propertyName for the response, not a security
        // check — but it's left as a defensive ResourceNotFoundException
        // in case of a data-integrity gap (e.g. an orphaned propertyId),
        // rather than assuming the property row must exist.
        Property property = propertyRepository.findById(unit.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found", ErrorCode.PROPERTY_NOT_FOUND
                ));

        // TODO: DEPOSIT_MONTHS is a hardcoded constant (2 months), not
        // sourced from any stored lease/deposit-terms entity. Flagged per
        // handoff §2.3 as a possible business-logic concern — out of scope
        // for this security fix, not changed here.
        BigDecimal deposit = unit.getRentAmount()
                .multiply(BigDecimal.valueOf(DEPOSIT_MONTHS));

        return UnitReservationSummaryResponse.builder()
                .unitId(unit.getId())
                .unitNumber(unit.getUnitNumber())
                .propertyName(property.getName())
                .monthlyRent(unit.getRentAmount())
                .depositAmount(deposit)
                .build();
    }
}