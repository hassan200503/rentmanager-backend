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
        Lease savedLease = leaseRepository.save(lease);
        eventPublisher.publishAll(savedLease.pullDomainEvents());

        Unit unit = unitRepository.findByIdAndTenantId(event.getUnitId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unit not found for id: " + event.getUnitId()));

        unit.markOccupied(event.getCorrelationId());
        Unit savedUnit = unitRepository.save(unit);
        eventPublisher.publishAll(savedUnit.pullDomainEvents());
    }
}