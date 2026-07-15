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
 * ---- Event-publishing fix (corrected 2026-07) ----
 *
 * unit.markPendingPayment(...) and unit.releasePendingPayment(...) both
 * correctly register a UnitOccupancyChangedEvent on the aggregate (see
 * Unit.java). A prior pass added eventPublisher.publishAll(...) calls after
 * each save, but pulled events from the object RETURNED BY save() rather
 * than the pre-save aggregate — UnitRepositoryAdapter.save() round-trips
 * through a mapper that rehydrates a fresh Unit instance with an empty
 * transient domainEvents list, so those calls were silently publishing
 * nothing despite looking correct. Both call sites below now pull events
 * from the pre-save `unit` reference instead.
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

        unit.markPendingPayment(intent.getId().toString());

        // FIX: pull from `unit` (pre-save) — see class comment above.
        var unitEvents = unit.pullDomainEvents();
        unitRepository.save(unit);

        eventPublisher.publishAll(unitEvents);

        entityManager.flush();

        log.info("Unit locked and marked PENDING_PAYMENT. unitId={} paymentIntentId={} tenantId={}",
                unit.getId(), intent.getId(), tenant.getId());

        return new UnitReservationResult(intent, unit.getUnitNumber(), darajaCredentials);
    }

    @Transactional
    public void attachCheckoutRequestId(UUID paymentIntentId, String checkoutRequestId) {
        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException(
                        "PaymentIntent vanished before checkoutRequestId could be attached: " + paymentIntentId));

        intent.attachCheckoutRequestId(checkoutRequestId);
        paymentIntentRepository.save(intent);
        entityManager.flush();
    }

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

        // FIX: pull from `unit` (pre-save) — see class comment above.
        var unitEvents = unit.pullDomainEvents();
        unitRepository.save(unit);

        eventPublisher.publishAll(unitEvents);

        entityManager.flush();

        log.error("STK push initiation failed; PaymentIntent marked FAILED and unit released. " +
                        "paymentIntentId={} unitId={}",
                paymentIntentId, intent.getUnitId(), cause);
    }
}