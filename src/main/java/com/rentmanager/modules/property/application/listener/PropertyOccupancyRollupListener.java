package com.rentmanager.modules.property.application.listener;

import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.event.UnitOccupancyChangedEvent;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

/**
 * Rolls unit-level occupancy up to the property level. Reacts to
 * UnitOccupancyChangedEvent (fired by Unit.markOccupied/markVacant/
 * markReserved/releaseReservation/markPendingPayment/releasePendingPayment)
 * and recomputes the parent Property's OccupancyStatus from the current
 * state of all its non-archived units.
 *
 * "Occupied" here means UnitOccupancyStatus.OCCUPIED only — RESERVED and
 * PENDING_PAYMENT are transitional states and are treated as not-yet-
 * occupied for this rollup.
 *
 * ASSUMPTION (flagging for product sign-off): units with UnitStatus.INACTIVE
 * still count toward the property's total unit count — only ARCHIVED units
 * are excluded. If inactive/unpublished units should NOT count toward
 * occupancy math, this filter needs tightening.
 *
 * Transaction handling mirrors DepositPaymentEventListener: AFTER_COMMIT +
 * REQUIRES_NEW, so this always observes the triggering unit's occupancy
 * change already durably committed, in its own transaction.
 *
 * IMPORTANT: PropertyCommandService.markFullyOccupied/markVacant/
 * markPartiallyOccupied all throw IllegalArgumentException if the property
 * is already in the target state, or if it's archived. This listener MUST
 * check current status first and skip the call when no change is needed —
 * otherwise a routine unit change that doesn't alter the property's overall
 * state would throw and roll back this listener's transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyOccupancyRollupListener {

    private final PropertyRepository propertyRepository;
    private final PropertyCommandService propertyCommandService;
    private final UnitRepository unitRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUnitOccupancyChanged(UnitOccupancyChangedEvent event) {

        UUID tenantId = event.getTenantId();
        UUID propertyId = event.getPropertyId();

        Property property = propertyRepository.findByIdAndTenantId(propertyId, tenantId)
                .orElse(null);

        if (property == null) {
            log.warn("PropertyOccupancyRollupListener: property {} not found for tenant {}, skipping rollup",
                    propertyId, tenantId);
            return;
        }

        // Archived properties are frozen — the validators reject any
        // occupancy mutation on them, so don't even attempt it.
        if (property.isArchived()) {
            return;
        }

        List<Unit> units = fetchAllUnitsForProperty(tenantId, propertyId);

        long totalUnits = units.stream()
                .filter(u -> u.getStatus() != UnitStatus.ARCHIVED)
                .count();

        long occupiedUnits = units.stream()
                .filter(u -> u.getStatus() != UnitStatus.ARCHIVED)
                .filter(u -> u.getOccupancyStatus() == UnitOccupancyStatus.OCCUPIED)
                .count();

        OccupancyStatus target;
        if (totalUnits == 0 || occupiedUnits == 0) {
            target = OccupancyStatus.VACANT;
        } else if (occupiedUnits == totalUnits) {
            target = OccupancyStatus.FULLY_OCCUPIED;
        } else {
            target = OccupancyStatus.PARTIALLY_OCCUPIED;
        }

        if (target == property.getOccupancyStatus()) {
            // Already correct — skip. Calling markX() again here would
            // throw IllegalArgumentException on a same-state transition.
            return;
        }

        switch (target) {
            case FULLY_OCCUPIED -> propertyCommandService.markFullyOccupied(tenantId, propertyId);
            case VACANT -> propertyCommandService.markVacant(tenantId, propertyId);
            case PARTIALLY_OCCUPIED -> propertyCommandService.markPartiallyOccupied(tenantId, propertyId);
        }
    }

    private List<Unit> fetchAllUnitsForProperty(UUID tenantId, UUID propertyId) {
        Page<Unit> page = unitRepository.findByTenantIdAndPropertyId(
                tenantId, propertyId, Pageable.unpaged()
        );
        return page.getContent();
    }
}