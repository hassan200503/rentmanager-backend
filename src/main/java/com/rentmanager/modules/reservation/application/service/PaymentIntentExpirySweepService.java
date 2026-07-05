package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Owns the single-PaymentIntent transactional unit of work for the stale-
 * intent sweep. Deliberately separated from PaymentIntentExpiryScheduler
 * rather than declared as a @Transactional method there — the scheduler
 * loops over multiple stale intents and calling a @Transactional method on
 * `this` from within that loop is a self-invocation that silently bypasses
 * Spring's proxy, meaning no transaction ever actually opens. This is the
 * exact same class of bug fixed earlier in
 * UnitReservationTransactionService / InitiateReservationServiceImpl —
 * it slipped back in here because this scheduler was written afterward
 * without applying the same split.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentExpirySweepService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final UnitRepository unitRepository;
    private final DomainEventPublisher eventPublisher;
    private final EntityManager entityManager;

    @Transactional
    public void expireOne(UUID paymentIntentId) {
        // Re-fetch inside the transaction rather than trusting the caller's
        // batch-read copy: guards against acting on a stale in-memory
        // snapshot if something else touched this intent between the batch
        // query and this method running (e.g. a very-late M-Pesa callback
        // arriving in the gap).
        PaymentIntent intent = paymentIntentRepository.findById(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException(
                        "PaymentIntent vanished during expiry sweep: " + paymentIntentId));

        if (intent.getStatus() != PaymentIntentStatus.PENDING) {
            log.info("PaymentIntent no longer PENDING, skipping. paymentIntentId={} currentStatus={}",
                    paymentIntentId, intent.getStatus());
            return;
        }

        intent.expire();
        paymentIntentRepository.save(intent);
        entityManager.flush();

        Unit unit = unitRepository.findByIdForUpdate(intent.getUnitId())
                .orElseThrow(() -> new IllegalStateException(
                        "Unit vanished during expiry sweep: " + intent.getUnitId()));

        if (unit.getOccupancyStatus() == UnitOccupancyStatus.PENDING_PAYMENT) {
            unit.releasePendingPayment(paymentIntentId.toString());
            unitRepository.save(unit);
            eventPublisher.publishAll(unit.pullDomainEvents());
            entityManager.flush();
            log.warn("Expired stale PaymentIntent and released unit (no M-Pesa callback received). " +
                    "paymentIntentId={} unitId={}", paymentIntentId, intent.getUnitId());
        } else {
            log.info("PaymentIntent expired but unit already moved on, no release needed. " +
                    "paymentIntentId={} unitId={} unitStatus={}",
                    paymentIntentId, intent.getUnitId(), unit.getOccupancyStatus());
        }
    }
}