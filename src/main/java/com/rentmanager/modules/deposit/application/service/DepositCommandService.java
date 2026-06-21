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

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(saved.pullDomainEvents());

        return saved;
    }

    public Deposit confirmPayment(UUID tenantId, UUID depositId, BigDecimal amountPaid) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit not found"));

        deposit.confirmPayment(amountPaid, generateCorrelationId());

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(saved.pullDomainEvents());

        return saved;
    }

    public Deposit refundDeposit(UUID tenantId, UUID depositId, BigDecimal refundAmount) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Deposit not found"));

        deposit.refund(refundAmount, generateCorrelationId());

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(saved.pullDomainEvents());

        return saved;
    }

    private String generateCorrelationId() {
        return "DEPOSIT-" + UUID.randomUUID();
    }
}