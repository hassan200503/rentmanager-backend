package com.rentmanager.modules.deposit.application.service;

import com.rentmanager.modules.deposit.domain.exception.DepositStateException;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.repository.TenantJpaRepository;
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
    private final TenantJpaRepository tenantJpaRepository;
    private final DomainEventPublisher eventPublisher;
    private final DarajaService darajaService;
    private final DarajaProperties darajaProperties;

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

    public Deposit refundDeposit(UUID tenantId, UUID depositId,
                                  BigDecimal deductionAmount, String deductionReason,
                                  String refundReference, String refundRemarks) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        BigDecimal effectiveDeduction = deductionAmount != null ? deductionAmount : BigDecimal.ZERO;

        if (effectiveDeduction.compareTo(BigDecimal.ZERO) > 0
                && (deductionReason == null || deductionReason.isBlank())) {
            throw new DepositStateException(
                    "Deduction reason is required when deducting from the deposit",
                    ErrorCode.DEPOSIT_DEDUCTION_REASON_REQUIRED
            );
        }

        BigDecimal refundAmount = deposit.getAmountPaid().subtract(effectiveDeduction);
        if (refundAmount.compareTo(BigDecimal.ZERO) > 0
                && (refundReference == null || refundReference.isBlank())) {
            throw new DepositStateException(
                    "M-Pesa transaction reference is required when issuing a refund",
                    ErrorCode.DEPOSIT_REFUND_REFERENCE_REQUIRED
            );
        }

        deposit.refund(effectiveDeduction, deductionReason, refundReference, refundRemarks, generateCorrelationId());

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

    /**
     * Initiates a deposit refund by sending an STK push to the landlord's own
     * M-Pesa phone. The landlord authorises the refund by entering their PIN;
     * when Safaricom confirms, {@link #completeRefundFromCallback} finalises
     * the deposit record automatically.
     *
     * <p>Money flow: landlord personal M-Pesa → landlord's own business shortcode
     * (via STK push). The tenant should be sent the refund separately via
     * M-Pesa "Send Money" or the platform's B2C flow.</p>
     *
     * @return the CheckoutRequestID to pass back to the client for status polling
     */
    public String initiateRefundViaStk(UUID tenantId, UUID depositId,
                                        String landlordPhone,
                                        BigDecimal deductionAmount,
                                        String deductionReason,
                                        String remarks) {

        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        BigDecimal effectiveDeduction = deductionAmount != null ? deductionAmount : BigDecimal.ZERO;

        if (effectiveDeduction.compareTo(BigDecimal.ZERO) > 0
                && (deductionReason == null || deductionReason.isBlank())) {
            throw new DepositStateException(
                    "Deduction reason is required when deducting from the deposit",
                    ErrorCode.DEPOSIT_DEDUCTION_REASON_REQUIRED
            );
        }

        BigDecimal refundAmount = deposit.getAmountPaid().subtract(effectiveDeduction);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DepositStateException(
                    "Refund amount must be > 0 — use Forfeit if the full deposit is being kept",
                    ErrorCode.DEPOSIT_REFUND_EXCEEDS_PAID
            );
        }

        TenantEntity landlord = tenantJpaRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        if (!landlord.getDarajaCredentials().isConfigured()) {
            throw new DepositStateException(
                    "Daraja credentials are not configured — set up M-Pesa credentials in Payment Settings first",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        String callbackUrl = darajaProperties.getDepositRefundCallbackUrl();
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new DepositStateException(
                    "Deposit refund callback URL is not configured",
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        String shortDepositId = depositId.toString().substring(0, 8).toUpperCase();
        String checkoutRequestId = darajaService.initiateSTKPush(
                landlordPhone,
                refundAmount,
                "DEP-REFUND-" + shortDepositId,
                "Deposit refund authorisation",
                landlord.getDarajaCredentials(),
                callbackUrl
        );

        // pendingRefundPhone stores the tenant's phone (the intended refund recipient).
        // It was loaded from TenantProfile when the request was enriched by the controller.
        // We use null here and rely on renterPhone from DepositResponse for display.
        deposit.markRefundPending(checkoutRequestId, null,
                effectiveDeduction, deductionReason, remarks);

        var events = deposit.pullDomainEvents();
        depositRepository.save(deposit);
        eventPublisher.publishAll(events);

        log.info("Deposit refund STK push initiated. depositId={} checkoutRequestId={} amount={}",
                depositId, checkoutRequestId, refundAmount);

        return checkoutRequestId;
    }

    /**
     * Called by the STK push callback handler when Safaricom confirms the
     * landlord's M-Pesa payment. Finalises the deposit refund with the
     * M-Pesa receipt as the audit reference.
     */
    public void completeRefundFromCallback(String checkoutRequestId, String mpesaReceipt) {
        Deposit deposit = depositRepository.findByPendingRefundCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No deposit found for checkout request: " + checkoutRequestId,
                        ErrorCode.DEPOSIT_NOT_FOUND));

        deposit.completePendingRefund(mpesaReceipt, generateCorrelationId());

        var events = deposit.pullDomainEvents();
        depositRepository.save(deposit);
        eventPublisher.publishAll(events);

        log.info("Deposit refund completed via STK callback. checkoutRequestId={} receipt={}",
                checkoutRequestId, mpesaReceipt);
    }

    /**
     * Cancels an in-flight refund STK push, allowing the landlord to retry
     * with corrected details or revert to the manual refund flow.
     */
    public void cancelPendingRefund(UUID tenantId, UUID depositId) {
        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        if (!deposit.hasPendingRefund()) {
            return;
        }
        deposit.clearPendingRefund();
        depositRepository.save(deposit);
        log.info("Pending deposit refund cancelled. depositId={}", depositId);
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