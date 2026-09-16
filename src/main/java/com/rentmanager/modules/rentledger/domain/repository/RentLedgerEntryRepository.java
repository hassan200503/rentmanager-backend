package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentLedgerEntryRepository {

    RentLedgerEntry save(RentLedgerEntry entry);

    Optional<RentLedgerEntry> findById(UUID id);

    Optional<RentLedgerEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Tenant-scoped read that takes a PESSIMISTIC_WRITE row lock for the
     * duration of the caller's transaction. Used by the disbursement flow to
     * serialize concurrent payout attempts against the same charge, which
     * would otherwise both read the same settleable amount and both pay out.
     */
    Optional<RentLedgerEntry> findByIdAndTenantIdForUpdate(UUID id, UUID tenantId);

    List<RentLedgerEntry> findAllByTenant(UUID tenantId);

    /**
     * Bulk lookup for mapping a page of {@code RentTransaction}s back to the
     * entries they were posted against — e.g. the tenant portal's payment
     * history, which needs each transaction's entry status and billing
     * period. One query per page rather than one per transaction.
     */
    List<RentLedgerEntry> findAllByTenantAndIdIn(UUID tenantId, List<UUID> ids);

    List<RentLedgerEntry> findByLease(UUID tenantId, UUID leaseId);

    /**
     * Idempotency guard for Phase 3's recurring scheduler: a (lease_id,
     * billing_period_start) uniqueness check so a re-run or overlapping
     * trigger never double-posts rent due for the same period.
     */
    Optional<RentLedgerEntry> findByLeaseIdAndBillingPeriodStart(UUID leaseId, LocalDate billingPeriodStart);

    /**
     * Used by Phase 4's overdue-detection scheduler: entries in DUE or
     * PARTIALLY_PAID whose dueDate + grace period has elapsed.
     */
    List<RentLedgerEntry> findByTenantAndStatusInAndDueDateLessThanEqual(
            UUID tenantId,
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    List<RentLedgerEntry> findByTenantAndStatus(UUID tenantId, RentLedgerStatus status);

    /**
     * Every currently unsettled (DUE/PARTIALLY_PAID/OVERDUE) or OVERPAID
     * entry for the tenant, across every lease, in one query — backs the
     * Tenants page's per-lease rent status without querying per lease.
     */
    List<RentLedgerEntry> findByTenantAndStatusIn(UUID tenantId, List<RentLedgerStatus> statuses);

    /**
     * Tenant-agnostic: the most recently posted entry for a lease, ordered
     * by billing period. Used by {@code RentChargeScheduler} to resume
     * posting from wherever it last left off, rather than replaying every
     * month of a lease's history on each run.
     */
    Optional<RentLedgerEntry> findLatestByLeaseId(UUID leaseId);

    /**
     * Tenant-agnostic sibling of {@code findByTenantAndStatusInAndDueDateLessThanEqual}
     * above, used by {@code RentOverdueScheduler}'s cross-tenant sweep.
     * Grace period (per-lease) is applied by the caller after this broad
     * fetch, not folded into this query.
     */
    List<RentLedgerEntry> findAllByStatusInAndDueDateLessThanEqual(
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    /**
     * Tenant-agnostic sweep bounded on both ends, for the rent reminder
     * cadence. The reminder sweep cares about a fixed window around today
     * (due dates from seven days ahead to seven days behind) and nothing
     * outside it, so an open-ended {@code <=} query would load every unpaid
     * entry in the system's history to send a handful of messages — cheap on
     * day one and a full-table scan by year two.
     */
    List<RentLedgerEntry> findAllByStatusInAndDueDateBetween(
            List<RentLedgerStatus> statuses,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    /**
     * Tenant-agnostic sweep for RentLedgerReconciliationScheduler: entries
     * touched since {@code threshold}, rather than the whole historical
     * table on every run — an entry that hasn't changed recently was
     * already reconciled correctly by a prior run (or has never had a
     * transaction posted against it, trivially reconciling at zero).
     */
    List<RentLedgerEntry> findAllByUpdatedAtAfter(Instant threshold);

    void delete(UUID id);
}