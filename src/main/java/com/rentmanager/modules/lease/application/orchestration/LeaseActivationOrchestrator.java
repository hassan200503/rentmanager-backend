package com.rentmanager.modules.lease.application.orchestration;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Bridges lease activation to the rent ledger module, following the same
 * saga-orchestrator convention already established by
 * {@code ReservationFulfillmentOrchestrator} for cross-module workflows —
 * neither module takes a direct dependency on the other's application
 * service.
 *
 * Deliberately NOT event-driven: {@code LeaseActivatedEvent} is registered
 * by {@code Lease} but nothing in this codebase currently publishes it
 * (neither {@code LeaseApplicationService} nor
 * {@code LeaseRepositoryImpl.save()} calls {@code pullDomainEvents()}), so
 * an {@code @EventListener} here would compile but never fire. This is
 * called directly by whatever activates the lease instead.
 *
 * KNOWN GAP: {@code Lease} has a second path to ACTIVE —
 * {@code activatePending()}, used by the (not-yet-reviewed) automatic
 * lease-activation scheduler referenced in {@code LeaseRepository}'s
 * javadoc. That call site is NOT wired to this orchestrator yet. Any lease
 * activated via that scheduler will not have its opening charge posted
 * until that scheduler is reviewed and updated to call
 * {@code onLeaseActivated} as well.
 */
@Service
@RequiredArgsConstructor
public class LeaseActivationOrchestrator {

    private final RentLedgerApplicationService rentLedgerApplicationService;

    /**
     * Posts the lease's opening rent charge. Runs in whatever transaction
     * the caller is already in (Spring's default REQUIRED propagation on
     * {@code RentLedgerApplicationService.postCharge}'s own
     * {@code @Transactional} means it joins rather than opens a new one) —
     * the caller is responsible for having its own {@code @Transactional}
     * boundary so the lease save and this charge commit atomically.
     *
     * Billing period: starts on the lease's own start date, ends on the
     * last day of that calendar month — the "genuine opening period"
     * shape that {@code RentLedgerApplicationService.postCharge} already
     * knows how to prorate. Due date is the same day as billingPeriodStart
     * — rent is due immediately at move-in, per product decision.
     */
    public void onLeaseActivated(UUID tenantId, Lease lease) {
        LocalDate billingPeriodStart = lease.getStartDate();
        LocalDate billingPeriodEnd = YearMonth.from(billingPeriodStart).atEndOfMonth();
        LocalDate dueDate = billingPeriodStart;

        String correlationId = "lease-activation-" + lease.getId();

        rentLedgerApplicationService.postCharge(
                tenantId,
                correlationId,
                lease.getId(),
                billingPeriodStart,
                billingPeriodEnd,
                dueDate
        );
    }
}