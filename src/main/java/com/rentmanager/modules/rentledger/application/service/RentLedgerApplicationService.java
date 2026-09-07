package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates {@link RentLedgerEntry} and {@link RentTransaction} across
 * their repository ports. This is the ONLY place that creates a
 * RENT_CHARGE transaction — per {@code RentLedgerEntry.create()}'s javadoc,
 * the domain entity deliberately does not construct its own opening
 * transaction, so the entry and its first transaction land atomically in
 * the same DB transaction here rather than the domain aggregate reaching
 * across to construct a sibling aggregate itself.
 *
 * Every public method here is a single {@code @Transactional} boundary:
 * mutate/create domain object(s), persist via the repository port(s),
 * then publish whatever domain events were registered. Events are
 * deliberately published from INSIDE the transaction (matching the
 * pattern already established in {@code MpesaCallbackService}) rather than
 * deferred to an after-commit hook — no such hook exists yet in this
 * codebase, and publishing here is consistent with existing code rather
 * than introducing a new pattern unasked for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentLedgerApplicationService {

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentTransactionRepository rentTransactionRepository;
    private final LeaseRepository leaseRepository;
    private final TenantRepository tenantRepository;
    private final DepositCommandService depositCommandService;
    private final DomainEventPublisher eventPublisher;

    // ------------------------------------------------------------------
    // Posting a charge (lease activation, and later Phase 3's recurring
    // scheduler — both call this same primitive with different period
    // bounds; this method has no opinion on WHEN a period should be
    // billed, only on HOW MUCH and how to post it).
    // ------------------------------------------------------------------

    /**
     * Creates a new {@link RentLedgerEntry} for the given billing period
     * and posts its opening {@code RENT_CHARGE} transaction atomically.
     *
     * Idempotent on (leaseId, billingPeriodStart): if an entry already
     * exists for this period — e.g. a re-run scheduler trigger, or this
     * method called twice for the same lease activation — the existing
     * entry is returned unchanged rather than posting a duplicate charge.
     * This mirrors the DB's own {@code uk_rent_ledger_entries_lease_period}
     * unique constraint, which is the ultimate backstop if this check
     * ever races.
     *
     * Proration: triggered only when {@code billingPeriodStart} equals
     * {@code lease.getStartDate()} AND that date is not the 1st of the
     * month — i.e. this is the lease's genuine opening period starting
     * mid-month. Any other billing period (including a full calendar
     * month that happens to be the lease's first if it starts on the 1st)
     * is charged the full {@code monthlyRent}. Uses actual calendar days
     * in the month, not a flat 30-day convention, so the amount is always
     * traceable against a real calendar.
     */
    @Transactional
    public RentLedgerEntry postCharge(
            UUID tenantId,
            String correlationId,
            UUID leaseId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            LocalDate dueDate
    ) {
        Optional<RentLedgerEntry> existing =
                rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, billingPeriodStart);
        if (existing.isPresent()) {
            log.info("postCharge is a no-op: entry already exists for leaseId={} billingPeriodStart={}",
                    leaseId, billingPeriodStart);
            return existing.get();
        }

        Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "lease not found for tenant: " + leaseId,
                        ErrorCode.LEASE_NOT_FOUND
                ));

        boolean prorated = billingPeriodStart.equals(lease.getStartDate())
                && billingPeriodStart.getDayOfMonth() > 1;

        BigDecimal amountDue = prorated
                ? prorate(lease.getRentAmount(), billingPeriodStart)
                : lease.getRentAmount();

        String currency = resolveCurrency(tenantId);

        RentLedgerEntry entry = RentLedgerEntry.create(
                tenantId,
                correlationId,
                leaseId,
                lease.getUnitId(),
                lease.getTenantProfileId(),
                billingPeriodStart,
                billingPeriodEnd,
                dueDate,
                amountDue,
                prorated,
                currency
        );
        // Drained before the save: save() returns a rehydrated instance with
        // an empty event list, so the creation event would otherwise be lost.
        List<com.rentmanager.domain.base.DomainEvent> pending = entry.pullDomainEvents();
        entry = rentLedgerEntryRepository.save(entry);

        RentTransaction chargeTransaction = RentTransaction.create(
                tenantId,
                entry.getId(),
                leaseId,
                RentTransactionType.RENT_CHARGE,
                amountDue,
                null, // no external reference — system-posted, not tied to a payment gateway callback
                RentTransactionSource.SYSTEM,
                "SYSTEM",
                billingPeriodStart.atStartOfDay(),
                currency
        );
        rentTransactionRepository.save(chargeTransaction);

        // Auto-credit overpayment from the prior period, if any, so the
        // deposit excess (or any overpayment) rolls forward to the next
        // entry rather than sitting on the old entry permanently.
        autoCreditOverpayment(tenantId, correlationId, leaseId, billingPeriodStart, entry);

        publish(entry, pending);
        return entry;
    }

    /**
     * Records a security deposit that was already collected (via M-Pesa
     * during the reservation flow, or by a landlord activating a lease with
     * a deposit already in hand). Posts a DEPOSIT-type RentTransaction so
     * the deposit still appears as a "Deposit" row in the transactions
     * dashboard, and creates the actual held-deposit record via
     * DepositCommandService — that Deposit aggregate (paid/refunded/
     * forfeited) is the real system of record for the money, not this
     * ledger row. The DEPOSIT transaction here is audit-only: it does NOT
     * affect the entry's amountPaid/status (see RentTransaction
     * .reducesBalanceOwed()'s javadoc for why a deposit must never be
     * counted as rent revenue).
     *
     * Idempotent: if a DEPOSIT transaction already exists for this lease
     * (checked by externalReference), this is a no-op. recordAlreadyCollectedDeposit
     * carries its own, separate idempotency guard.
     */
    @Transactional
    public void postDeposit(
            UUID tenantId,
            String correlationId,
            UUID leaseId,
            BigDecimal depositAmount
    ) {
        postDeposit(tenantId, correlationId, leaseId, depositAmount, null);
    }

    @Transactional
    public void postDeposit(
            UUID tenantId,
            String correlationId,
            UUID leaseId,
            BigDecimal depositAmount,
            String mpesaReceiptNumber
    ) {
        String depositRef = mpesaReceiptNumber != null ? mpesaReceiptNumber : "deposit-" + leaseId;
        List<RentTransaction> existingDeposits = rentTransactionRepository.findByLease(tenantId, leaseId)
                .stream()
                .filter(t -> t.getType() == RentTransactionType.DEPOSIT)
                .toList();
        if (!existingDeposits.isEmpty()) {
            log.info("postDeposit is a no-op: deposit already recorded for leaseId={}", leaseId);
            return;
        }

        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(tenantId, leaseId);

        // Drained before save in the create branch — save() returns a rehydrated
        // instance with an empty event list, same as postCharge lines 137-138.
        List<com.rentmanager.domain.base.DomainEvent> pending = new ArrayList<>();

        RentLedgerEntry entry;
        if (entries.isEmpty()) {
            Lease lease = leaseRepository.findByIdAndTenantId(leaseId, tenantId)
                    .orElseThrow(() -> new RentLedgerStateException(
                            "lease not found: " + leaseId, ErrorCode.LEASE_NOT_FOUND));

            LocalDate billingPeriodStart = lease.getStartDate();
            LocalDate billingPeriodEnd = YearMonth.from(billingPeriodStart).atEndOfMonth();

            boolean prorated = billingPeriodStart.getDayOfMonth() > 1;
            BigDecimal amountDue = prorated
                    ? prorate(lease.getRentAmount(), billingPeriodStart)
                    : lease.getRentAmount();

            String currency = resolveCurrency(tenantId);

            entry = RentLedgerEntry.create(
                    tenantId, correlationId, leaseId, lease.getUnitId(),
                    lease.getTenantProfileId(), billingPeriodStart, billingPeriodEnd,
                    billingPeriodStart, amountDue, prorated, currency
            );
            pending = entry.pullDomainEvents();
            entry = rentLedgerEntryRepository.save(entry);

            RentTransaction chargeTransaction = RentTransaction.create(
                    tenantId, entry.getId(), leaseId,
                    RentTransactionType.RENT_CHARGE, amountDue,
                    null, RentTransactionSource.SYSTEM, "SYSTEM",
                    billingPeriodStart.atStartOfDay(), currency
            );
            rentTransactionRepository.save(chargeTransaction);
        } else {
            entry = entries.get(0);
        }

        RentTransactionSource source = mpesaReceiptNumber != null
                ? RentTransactionSource.MPESA
                : RentTransactionSource.SYSTEM;

        RentTransaction depositTransaction = RentTransaction.create(
                tenantId,
                entry.getId(),
                leaseId,
                RentTransactionType.DEPOSIT,
                depositAmount,
                depositRef,
                source,
                "SYSTEM",
                LocalDateTime.now(),
                entry.getCurrency()
        );

        try {
            rentTransactionRepository.save(depositTransaction);
        } catch (DataIntegrityViolationException e) {
            log.info("postDeposit: external_reference uniqueness constraint caught a concurrent duplicate. leaseId={}", leaseId);
            return;
        }

        depositCommandService.recordAlreadyCollectedDeposit(
                tenantId, leaseId, entry.getUnitId(), entry.getTenantProfileId(), depositAmount, correlationId
        );

        publish(entry, pending);
    }

    /**
     * daysInMonth and occupiedDays are both computed against
     * billingPeriodStart's calendar month — correct as long as the lease's
     * opening period never spans a month boundary (billingPeriodEnd is
     * expected to be the last day of billingPeriodStart's month, which the
     * caller is responsible for). This method has no way to detect a
     * caller passing a cross-month period; that invariant lives with
     * whoever computes billingPeriodEnd before calling postCharge().
     */
    private BigDecimal prorate(BigDecimal monthlyRent, LocalDate billingPeriodStart) {
        int daysInMonth = YearMonth.from(billingPeriodStart).lengthOfMonth();
        int occupiedDays = daysInMonth - billingPeriodStart.getDayOfMonth() + 1;

        return monthlyRent
                .multiply(BigDecimal.valueOf(occupiedDays))
                .divide(BigDecimal.valueOf(daysInMonth), 2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Applying balance-reducing transactions (PAYMENT, WAIVER, CREDIT_APPLIED)
    // ------------------------------------------------------------------

    /**
     * Records a {@code PAYMENT}, {@code WAIVER}, or {@code CREDIT_APPLIED}
     * transaction against a ledger entry. Type validity beyond "reduces
     * balance owed" is enforced by {@code RentLedgerEntry.applyTransaction}
     * itself — not re-validated here, to avoid duplicating a rule that
     * already lives in exactly one place in the domain layer.
     *
     * Idempotent on {@code externalReference} when one is supplied (the
     * M-Pesa case): checked here as a fast pre-check, and backstopped by
     * the DB's partial unique index
     * ({@code uk_rent_transactions_tenant_external_reference}) in case two
     * concurrent callback deliveries both pass the pre-check before either
     * commits. A constraint violation on save is caught and treated as a
     * duplicate delivery, not an error.
     */
    @Transactional
    public RentLedgerEntry applyTransaction(
            UUID tenantId,
            String correlationId,
            UUID ledgerEntryId,
            RentTransactionType type,
            BigDecimal amount,
            String externalReference,
            RentTransactionSource source,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        if (externalReference != null) {
            Optional<RentTransaction> duplicate =
                    rentTransactionRepository.findByExternalReference(tenantId, externalReference);
            if (duplicate.isPresent()) {
                log.info("applyTransaction is a no-op: externalReference already recorded. " +
                                "tenantId={} externalReference={}",
                        tenantId, externalReference);
                return rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                        .orElseThrow(() -> entryNotFound(ledgerEntryId));
            }
        }

        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(ledgerEntryId));

        RentTransaction transaction = RentTransaction.create(
                tenantId,
                entry.getId(),
                entry.getLeaseId(),
                type,
                amount,
                externalReference,
                source,
                recordedBy,
                occurredAt,
                entry.getCurrency()
        );

        entry.applyTransaction(correlationId, transaction);

        try {
            rentTransactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            // Lost the race: another concurrent delivery for the same
            // externalReference committed first. Not an error — return the
            // entry as it stands rather than propagating a 500 for what is,
            // from the caller's perspective, a successfully-processed
            // duplicate.
            log.info("applyTransaction: external_reference uniqueness constraint caught a concurrent " +
                            "duplicate. tenantId={} externalReference={}",
                    tenantId, externalReference);
            return rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                    .orElseThrow(() -> entryNotFound(ledgerEntryId));
        }

        List<com.rentmanager.domain.base.DomainEvent> pending = entry.pullDomainEvents();
        entry = rentLedgerEntryRepository.save(entry);
        publish(entry, pending);
        return entry;
    }

    // ------------------------------------------------------------------
    // Adjustments to the charge itself
    // ------------------------------------------------------------------

    /**
     * @param delta signed — positive increases amountDue, negative
     *              decreases it. The transaction's own {@code amount} is
     *              always stored positive ({@code delta.abs()}), per
     *              RentTransaction's contract; the sign is carried
     *              separately into {@code RentLedgerEntry.applyAdjustment}.
     */
    @Transactional
    public RentLedgerEntry applyAdjustment(
            UUID tenantId,
            String correlationId,
            UUID ledgerEntryId,
            BigDecimal delta,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(ledgerEntryId));

        RentTransaction transaction = RentTransaction.create(
                tenantId,
                entry.getId(),
                entry.getLeaseId(),
                RentTransactionType.ADJUSTMENT,
                delta.abs(),
                null,
                RentTransactionSource.ADMIN_ADJUSTMENT,
                recordedBy,
                occurredAt,
                entry.getCurrency()
        );

        entry.applyAdjustment(correlationId, transaction, delta);

        rentTransactionRepository.save(transaction);
        List<com.rentmanager.domain.base.DomainEvent> pending = entry.pullDomainEvents();
        entry = rentLedgerEntryRepository.save(entry);
        publish(entry, pending);
        return entry;
    }

    // ------------------------------------------------------------------
    // Time-driven transition
    // ------------------------------------------------------------------

    @Transactional
    public RentLedgerEntry markOverdue(
            UUID tenantId,
            String correlationId,
            UUID ledgerEntryId,
            int daysOverdue
    ) {
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(ledgerEntryId));

        entry.markOverdue(correlationId, daysOverdue);

        List<com.rentmanager.domain.base.DomainEvent> pending = entry.pullDomainEvents();
        entry = rentLedgerEntryRepository.save(entry);
        publish(entry, pending);
        return entry;
    }

    // ------------------------------------------------------------------
    // Overpayment resolution
    // ------------------------------------------------------------------

    @Transactional
    public RentLedgerEntry resolveOverpaymentWithRefund(
            UUID tenantId,
            UUID ledgerEntryId,
            BigDecimal refundAmount,
            String externalReference,
            RentTransactionSource source,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(ledgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(ledgerEntryId));

        RentTransaction refundTransaction = RentTransaction.create(
                tenantId,
                entry.getId(),
                entry.getLeaseId(),
                RentTransactionType.REFUND,
                refundAmount,
                externalReference,
                source,
                recordedBy,
                occurredAt,
                entry.getCurrency()
        );

        entry.resolveOverpaymentWithRefund(refundTransaction);

        rentTransactionRepository.save(refundTransaction);
        // No event published here: resolveOverpaymentWithRefund() registers
        // none by design (see RentLedgerEntry javadoc) — the OVERPAID->PAID
        // transition is visible to any consumer reading status directly.
        return rentLedgerEntryRepository.save(entry);
    }

    /**
     * Resolves an OVERPAID {@code sourceEntry} by applying its excess as a
     * {@code CREDIT_APPLIED} transaction on a DIFFERENT, future
     * {@code targetEntry} — per the locked design, these two aggregates are
     * mutated together in one DB transaction here, since no single domain
     * method spans both.
     */
    @Transactional
    public void resolveOverpaymentAsCredit(
            UUID tenantId,
            String correlationId,
            UUID sourceLedgerEntryId,
            UUID targetLedgerEntryId,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        RentLedgerEntry sourceEntry = rentLedgerEntryRepository.findByIdAndTenantId(sourceLedgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(sourceLedgerEntryId));
        RentLedgerEntry targetEntry = rentLedgerEntryRepository.findByIdAndTenantId(targetLedgerEntryId, tenantId)
                .orElseThrow(() -> entryNotFound(targetLedgerEntryId));

        BigDecimal excess = sourceEntry.getExcessAmount();

        RentTransaction creditTransaction = RentTransaction.create(
                tenantId,
                targetEntry.getId(),
                targetEntry.getLeaseId(),
                RentTransactionType.CREDIT_APPLIED,
                excess,
                null,
                RentTransactionSource.ADMIN_ADJUSTMENT,
                recordedBy,
                occurredAt,
                targetEntry.getCurrency()
        );

        targetEntry.applyTransaction(correlationId, creditTransaction);
        sourceEntry.resolveOverpaymentAsCredit();

        rentTransactionRepository.save(creditTransaction);
        rentLedgerEntryRepository.save(targetEntry);
        rentLedgerEntryRepository.save(sourceEntry);

        publish(targetEntry);
        // sourceEntry registers no event on resolveOverpaymentAsCredit() by
        // design — its status change is visible to any direct reader.
    }

    // ------------------------------------------------------------------
    // Transaction reversal (never a hard delete — see RentTransaction's
    // append-only contract)
    // ------------------------------------------------------------------

    /**
     * Voids a transaction by posting a compensating REVERSAL transaction
     * against it, rather than deleting it. Recalculates the parent ledger
     * entry's {@code amountPaid} and {@code status} to reflect the reversal.
     * Both the original and the reversal row remain in the table
     * afterward, so the original's {@code external_reference} keeps
     * blocking replay of the same M-Pesa receipt.
     *
     * RENT_CHARGE transactions cannot be reversed this way (the entry must
     * remain intact). ADJUSTMENT transactions cannot be reversed because
     * the direction (signed delta) is not stored on the transaction record.
     * Idempotent: reversing an already-reversed transaction is a no-op.
     */
    @Transactional
    public void reverseTransaction(UUID tenantId, UUID transactionId, String recordedBy) {
        RentTransaction original = rentTransactionRepository.findByIdAndTenantId(transactionId, tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "transaction not found: " + transactionId,
                        ErrorCode.RENT_TRANSACTION_NOT_FOUND
                ));

        if (rentTransactionRepository.findByReversesTransactionId(tenantId, original.getId()).isPresent()) {
            log.info("reverseTransaction is a no-op: transactionId={} already has a reversal posted", transactionId);
            return;
        }

        RentLedgerEntry entry = rentLedgerEntryRepository.findByIdAndTenantId(
                        original.getLedgerEntryId(), tenantId)
                .orElseThrow(() -> new RentLedgerStateException(
                        "rent ledger entry not found: " + original.getLedgerEntryId(),
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        RentTransaction reversal = RentTransaction.reversalOf(original, tenantId, recordedBy, LocalDateTime.now());

        entry.reverseTransaction(original, reversal);

        try {
            rentTransactionRepository.save(reversal);
        } catch (DataIntegrityViolationException e) {
            log.info("reverseTransaction: uk_rent_transactions_reverses_transaction_id caught a concurrent " +
                            "duplicate reversal. tenantId={} transactionId={}", tenantId, transactionId);
            return;
        }

        rentLedgerEntryRepository.save(entry);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void autoCreditOverpayment(
            UUID tenantId,
            String correlationId,
            UUID leaseId,
            LocalDate billingPeriodStart,
            RentLedgerEntry currentEntry
    ) {
        List<RentLedgerEntry> entries = rentLedgerEntryRepository.findByLease(tenantId, leaseId);
        RentLedgerEntry previousEntry = null;
        for (RentLedgerEntry e : entries) {
            if (e.getBillingPeriodEnd().isBefore(billingPeriodStart)
                    && (previousEntry == null
                    || e.getBillingPeriodEnd().isAfter(previousEntry.getBillingPeriodEnd()))) {
                previousEntry = e;
            }
        }
        if (previousEntry != null
                && previousEntry.getExcessAmount().compareTo(BigDecimal.ZERO) > 0) {
            resolveOverpaymentAsCredit(
                    tenantId,
                    correlationId + "-auto-credit",
                    previousEntry.getId(),
                    currentEntry.getId(),
                    "SYSTEM",
                    LocalDateTime.now()
            );
        }
    }

    private void publish(RentLedgerEntry entry) {
        List<com.rentmanager.domain.base.DomainEvent> events = new ArrayList<>(entry.pullDomainEvents());
        if (!events.isEmpty()) {
            eventPublisher.publishAll(events);
        }
    }

    /**
     * Publishes events drained from the aggregate BEFORE it was saved, plus
     * anything registered on it since.
     *
     * <p>{@code RentLedgerEntryRepositoryAdapter.save()} returns
     * {@code mapper.toDomain(saved)} — a rehydrated instance whose
     * {@code AggregateRoot.domainEvents} list is new and empty. So
     * {@code entry = repository.save(entry); publish(entry);} pulled events
     * from an object that never had any, and published nothing at all. Every
     * downstream listener was silently dead: no tax invoice was ever
     * generated from a rent payment, and no payment notification was ever
     * sent. The reservation module hit this exact bug and documents it on
     * {@code UnitReservationTransactionService}; this is the same mistake in
     * the rent ledger.
     *
     * <p>Draining before the save is what makes it correct. Anything the
     * post-save instance accumulated afterwards is still collected here, so
     * ordering and completeness both hold.
     */
    private void publish(RentLedgerEntry entry, List<com.rentmanager.domain.base.DomainEvent> pending) {
        List<com.rentmanager.domain.base.DomainEvent> events = new ArrayList<>(pending);
        events.addAll(entry.pullDomainEvents());
        if (!events.isEmpty()) {
            eventPublisher.publishAll(events);
        }
    }

    private RentLedgerStateException entryNotFound(UUID ledgerEntryId) {
        return new RentLedgerStateException(
                "rent ledger entry not found: " + ledgerEntryId,
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    /**
     * The landlord's configured currency, read once per ledger-entry-creating
     * call and then carried onto every RentTransaction posted against that
     * entry (see the currency-explicit RentLedgerEntry/RentTransaction
     * create() overloads) rather than re-looked-up per transaction — a
     * ledger entry's currency doesn't change transaction to transaction.
     * Falls back to "KES" if the tenant has none set, matching
     * CreateTenantCommandHandler's own default for new tenants.
     */
    private String resolveCurrency(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(com.rentmanager.modules.tenant.domain.model.Tenant::getCurrency)
                .filter(currency -> currency != null && !currency.isBlank())
                .orElse("KES");
    }
}