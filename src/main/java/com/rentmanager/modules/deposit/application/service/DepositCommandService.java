package com.rentmanager.modules.deposit.application.service;

import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DepositCommandService {

    private final DepositRepository depositRepository;
    private final TenantRepository tenantRepository;
    private final DomainEventPublisher eventPublisher;

    public Deposit createDeposit(UUID tenantId, UUID leaseId, UUID unitId,
                                 UUID tenantProfileId, BigDecimal amountRequired) {

        String correlationId = generateCorrelationId();
        String currency = resolveCurrency(tenantId);

        Deposit deposit = Deposit.create(
                tenantId, leaseId, unitId, tenantProfileId, amountRequired, correlationId, currency
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
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        deposit.confirmPayment(amountPaid, generateCorrelationId());

        // FIX: pull from pre-save `deposit`.
        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    public Deposit refundDeposit(UUID tenantId, UUID depositId, BigDecimal refundAmount) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        deposit.refund(refundAmount, generateCorrelationId());

        // FIX: pull from pre-save `deposit`.
        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    public Deposit forfeitDeposit(UUID tenantId, UUID depositId) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        deposit.forfeit(generateCorrelationId());

        var events = deposit.pullDomainEvents();

        Deposit saved = depositRepository.save(deposit);

        eventPublisher.publishAll(events);

        return saved;
    }

    /**
     * Records a deposit that was already collected through a different flow
     * — today, the M-Pesa reservation deposit or a landlord activating a
     * lease with a deposit already in hand (see
     * RentLedgerApplicationService#postDeposit, the only caller). Creates
     * the Deposit straight into HELD status rather than going through
     * create() (UNPAID) then confirmPayment() (HELD): the money is already
     * considered received by the time this runs, so there's no real
     * "awaiting payment" window to model.
     *
     * Deliberately drains and does NOT publish the domain events this
     * registers (DepositCreatedEvent, DepositPaidEvent) — unlike every
     * other method here. DepositPaidEvent has an existing listener
     * (DepositPaymentEventListener) that activates the lease and marks the
     * unit occupied on receipt, which is exactly what
     * ReservationFulfillmentOrchestrator / LeaseActivationOrchestrator
     * already do, deliberately, as a separate later step (a lease stays
     * PENDING_ACTIVATION after its deposit is paid — activation is a
     * distinct action, not an automatic consequence of payment). Publishing
     * here would fire that listener early and race the real activation
     * path. If a future listener needs to react to "deposit collected"
     * (e.g. an SMS receipt), give it its own event rather than reusing
     * DepositPaidEvent for both meanings.
     *
     * Idempotent: a lease has at most one deposit (see
     * DepositRepository#findByLeaseId), so a second call for the same
     * lease returns the existing one unchanged.
     */
    public Deposit recordAlreadyCollectedDeposit(
            UUID tenantId, UUID leaseId, UUID unitId, UUID tenantProfileId,
            BigDecimal amountCollected, String correlationId
    ) {
        Optional<Deposit> existing = depositRepository.findByLeaseId(leaseId);
        if (existing.isPresent()) {
            log.info("recordAlreadyCollectedDeposit is a no-op: deposit already recorded for leaseId={}", leaseId);
            return existing.get();
        }

        String currency = resolveCurrency(tenantId);
        Deposit deposit = Deposit.create(
                tenantId, leaseId, unitId, tenantProfileId, amountCollected, correlationId, currency
        );
        deposit.confirmPayment(amountCollected, correlationId);
        deposit.pullDomainEvents(); // drained, not published — see javadoc above

        return depositRepository.save(deposit);
    }

    private String resolveCurrency(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(com.rentmanager.modules.tenant.domain.model.Tenant::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .orElse("KES");
    }

    private String generateCorrelationId() {
        return "DEPOSIT-" + UUID.randomUUID();
    }
}