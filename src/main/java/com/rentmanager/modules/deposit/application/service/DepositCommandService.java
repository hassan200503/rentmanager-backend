package com.rentmanager.modules.deposit.application.service;

import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class DepositCommandService {

    private final DepositRepository depositRepository;
    private final DomainEventPublisher eventPublisher;

    public Deposit createDeposit(UUID tenantId, UUID leaseId, UUID unitId,
                                 UUID tenantProfileId, BigDecimal amountRequired) {

        String correlationId = generateCorrelationId();

        Deposit deposit = Deposit.create(
                tenantId, leaseId, unitId, tenantProfileId, amountRequired, correlationId
        );

        // FIX: pull from `deposit` (pre-save) — DepositRepositoryAdapter.save()
        // returns Deposit.rehydrate(...), a fresh instance with an empty
        // transient domainEvents list, so pulling from `saved` always
        // silently published nothing.
        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    public Deposit confirmPayment(UUID tenantId, UUID depositId, BigDecimal amountPaid) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit not found"));

        deposit.confirmPayment(amountPaid, generateCorrelationId());

        // FIX: pull from pre-save `deposit`.
        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    public Deposit refundDeposit(UUID tenantId, UUID depositId, BigDecimal refundAmount) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit not found"));

        deposit.refund(refundAmount, generateCorrelationId());

        // FIX: pull from pre-save `deposit`.
        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    private String generateCorrelationId() {
        return "DEPOSIT-" + UUID.randomUUID();
    }
}