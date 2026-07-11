package com.rentmanager.modules.reservation.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rentmanager.modules.reservation.domain.enums.UnitReservationResult;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Owns the short-lived, lock-holding transactional steps of the reservation
 * initiation flow. Deliberately separated from
 * {@link InitiateReservationServiceImpl} rather than declared as private
 * @Transactional methods there: Spring's @Transactional is proxy-based, and
 * a self-invoked call (this.someTransactionalMethod()) from within the same
 * bean bypasses the proxy silently — no exception, no warning, just a
 * transaction that quietly never opens. Splitting into a separate bean is
 * the only way to guarantee these boundaries are honored.
 *
 * Each method here is intentionally short and never wraps an external HTTP
 * call (e.g. the Daraja STK push). Holding a PESSIMISTIC_WRITE row lock
 * across a slow external call would serialize all reservation attempts on
 * that unit for the duration of that call, turning a Safaricom slowdown
 * into a platform-wide reservation outage for that unit.
 *
 * ---- Event-publishing fix (broader event-publish sweep, item 4.3) ----
 *
 * unit.markPendingPayment(...) and unit.releasePendingPayment(...) both
 * correctly register a UnitOccupancyChangedEvent on the aggregate (see
 * Unit.java), but this class previously had no DomainEventPublisher at all
 * and never called saved.pullDomainEvents() after either save — the events
 * were registered and then silently discarded at commit. This was the same
 * shape as the pre-fix lease bug (project handoff §2.9): the aggregate
 * looked clean in isolation, and the gap only surfaced by checking the
 * calling service.
 *
 * This was also an inconsistency in its own right: releasePendingPayment is
 * called from two call sites for the same aggregate — MpesaCallbackService
 * (which did publish) and releaseUnitAndFailIntent below (which did not).
 * Both call sites now publish identically.
 *
 * eventPublisher.publishAll(unit.pullDomainEvents()) is added directly after
 * each unitRepository.save(unit) call, inside the same transaction boundary,
 * matching the convention used by every other command service in this
 * sweep (Property, Unit's other methods, Deposit, and the fixed Lease path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnitReservationTransactionService {

    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final DomainEventPublisher eventPublisher;

    private static final int DEPOSIT_MONTHS = 2;

    /**
     * Acquires a PESSIMISTIC_WRITE lock on the unit row, validates it is
     * VACANT, resolves the owning landlord's Daraja credentials (failing
     * clearly if not yet configured — deliberately never falling back to
     * any shared/default credentials, since that could route a real
     * tenant's deposit into the wrong account), and atomically flips the
     * unit to PENDING_PAYMENT while creating the PaymentIntent — all inside
     * one short transaction.
     *
     * The PaymentIntent's tenantId is taken directly from this already
     * row-locked Unit (unit.getTenantId()), not from the caller's request
     * or any auth context — this endpoint is unauthenticated (a prospective
     * renter has no session yet), so the owning unit is the only trustworthy
     * source of tenancy for the intent being created.
     *
     * Concurrent callers for the same unit serialize on the row lock: the
     * second caller blocks until this transaction commits, then re-reads
     * the now-PENDING_PAYMENT status and is correctly rejected instead of
     * racing ahead.
     *
     * Returns the unit's display number and the landlord's Daraja
     * credentials alongside the PaymentIntent, since neither
     * InitiateReservationRequest nor the caller otherwise has access to
     * either once this lock-holding transaction has committed and the lock
     * released.
     */
    @Transactional
    public UnitReservationResult reserveUnitAndCreateIntent(InitiateReservationRequest request) {

        Unit unit = unitRepository.findByIdForUpdate(request.unitId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit not found", ErrorCode.UNIT_NOT_FOUND
                ));

        if (unit.getOccupancyStatus() != com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus.VACANT) {
            throw new IllegalStateException(
                    "Unit is not available for reservation: " + unit.getId() +
                            " (current status: " + unit.getOccupancyStatus() + ")"
            );
        }

        Tenant tenant = tenantRepository.findById(unit.getTenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant (landlord) not found for unit: " + unit.getId()));

        DarajaCredentials darajaCredentials = tenant.getDarajaCredentials();
        if (darajaCredentials == null || !darajaCredentials.isConfigured()) {
            // Deliberately fails here rather than falling back to any
            // shared/sandbox credentials — silently doing so would risk
            // routing a real tenant's deposit into the wrong M-Pesa
            // account. The landlord must complete Daraja setup before
            // their properties can accept reservations.
            throw new IllegalStateException(
                    "This property is not yet accepting payments: landlord has not " +
                            "configured M-Pesa credentials. tenantId=" + tenant.getId()
            );
        }

        BigDecimal depositAmount = unit.getRentAmount()
                .multiply(BigDecimal.valueOf(DEPOSIT_MONTHS));

        String formDataJson;
        try {
            formDataJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize reservation form data", e);
        }

        PaymentIntent intent = PaymentIntent.create(
                unit.getTenantId(),
                request.unitId(),
                unit.getPropertyId(),
                formDataJson,
                depositAmount
        );
        intent = paymentIntentRepository.save(intent);

        // correlationId: using the PaymentIntent id gives every downstream
        // UnitOccupancyChangedEvent for this reservation attempt a stable,
        // traceable id from the very first state transition.
        unit.markPendingPayment(intent.getId().toString());
        Unit savedUnit = unitRepository.save(unit);

        eventPublisher.publishAll(savedUnit.pullDomainEvents());

        // Force the flush inside this transaction so the row-lock-protected
        // write (and any constraint violation) surfaces here, under this
        // method's control, rather than silently at commit time after the
        // caller has already moved on to the external Daraja call.
        entityManager.flush();

        log.info("Unit locked and marked PENDING_PAYMENT. unitId={} paymentIntentId={} tenantId={}",
                unit.getId(), intent.getId(), tenant.getId());

        return new UnitReservationResult(intent, unit.getUnitNumber(), darajaCredentials);
    }

    /**
     * Attaches the Daraja checkoutRequestId to an already-created
     * PaymentIntent. Runs in its own transaction, after the external STK
     * push call has already returned, so this fast DB write is never
     * blocked behind Daraja latency.
     */
    @Transactional
    public void attachCheckoutRequestId(UUID paymentIntentId, String checkoutRequestId) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException(
                        "PaymentIntent vanished before checkoutRequestId could be attached: " + paymentIntentId));

        intent.attachCheckoutRequestId(checkoutRequestId);
        paymentIntentRepository.save(intent);
        entityManager.flush();
    }

    /**
     * Compensating action for when the STK push call itself fails (network
     * error, Daraja rejects the request, timeout before Safaricom even
     * accepts it) — as opposed to a later async callback reporting payment
     * failure, which MpesaCallbackService handles separately. Marks the
     * intent FAILED and releases the unit back to VACANT so it's
     * immediately reservable again, rather than stuck in PENDING_PAYMENT
     * until the scheduled sweep catches it.
     */
    @Transactional
    public void releaseUnitAndFailIntent(UUID paymentIntentId, Throwable cause) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException(
                        "PaymentIntent vanished during failure compensation: " + paymentIntentId));

        intent.markFailed();
        paymentIntentRepository.save(intent);

        Unit unit = unitRepository.findByIdForUpdate(intent.getUnitId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unit vanished during failure compensation: " + intent.getUnitId()));

        unit.releasePendingPayment(intent.getId().toString());
        Unit savedUnit = unitRepository.save(unit);

        eventPublisher.publishAll(savedUnit.pullDomainEvents());

        entityManager.flush();

        log.error("STK push initiation failed; PaymentIntent marked FAILED and unit released. " +
                        "paymentIntentId={} unitId={}",
                paymentIntentId, intent.getUnitId(), cause);
    }
}