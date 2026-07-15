package com.rentmanager.modules.deposit.application.listener;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.deposit.domain.event.DepositPaidEvent;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DepositPaymentEventListener {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final DomainEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDepositPaid(DepositPaidEvent event) {

        UUID tenantId = event.getTenantId();

        Lease lease = leaseRepository.findByIdAndTenantId(event.getLeaseId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Lease not found for id: " + event.getLeaseId()));

        lease.activate();

        // FIX: pull events from `lease` (pre-save, live domainEvents list),
        // not from the object returned by save() — repository save() paths
        // in this codebase round-trip through a mapper that rehydrates a
        // fresh aggregate with an empty transient domainEvents list, so
        // pulling from the post-save object always silently returns nothing.
        var leaseEvents = lease.pullDomainEvents();
        leaseRepository.save(lease);
        eventPublisher.publishAll(leaseEvents);

        Unit unit = unitRepository.findByIdAndTenantId(event.getUnitId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unit not found for id: " + event.getUnitId()));

        unit.markOccupied(event.getCorrelationId());

        // FIX: same reasoning as above — pull from pre-save `unit`.
        var unitEvents = unit.pullDomainEvents();
        unitRepository.save(unit);
        eventPublisher.publishAll(unitEvents);
    }
}