package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentLedgerEntryJpaRepository extends JpaRepository<RentLedgerEntryJpaEntity, UUID> {

    Optional<RentLedgerEntryJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Row-locking read for the B2C disbursement flow. Acquires a
     * PESSIMISTIC_WRITE lock on the ledger entry for the duration of the
     * caller's transaction, so two concurrent disbursement attempts against
     * the same charge serialize instead of both reading the same
     * settleable amount and both paying out.
     *
     * Uses an explicit @Query rather than a derived method name because
     * @Lock is not reliably honored on derived queries across Hibernate
     * versions — it needs a JPQL query to attach to.
     *
     * Callers must keep the enclosing transaction short and must never hold
     * this lock across the Daraja B2C call. See
     * DisbursementTransactionService for the split that guarantees it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000")})
    @Query("SELECT e FROM RentLedgerEntryJpaEntity e WHERE e.id = :id AND e.tenantId = :tenantId")
    Optional<RentLedgerEntryJpaEntity> findByIdAndTenantIdForUpdate(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    List<RentLedgerEntryJpaEntity> findAllByTenantId(UUID tenantId);

    List<RentLedgerEntryJpaEntity> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

    List<RentLedgerEntryJpaEntity> findByTenantIdAndLeaseId(UUID tenantId, UUID leaseId);

    Optional<RentLedgerEntryJpaEntity> findByLeaseIdAndBillingPeriodStart(UUID leaseId, LocalDate billingPeriodStart);

    List<RentLedgerEntryJpaEntity> findByTenantIdAndStatusInAndDueDateLessThanEqual(
            UUID tenantId,
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    List<RentLedgerEntryJpaEntity> findByTenantIdAndStatus(UUID tenantId, RentLedgerStatus status);

    /**
     * Backs {@code RentLedgerEntryRepository.findLatestByLeaseId} — used by
     * {@code RentChargeScheduler} to resume posting from the most recent
     * period already on record for a lease.
     */
    Optional<RentLedgerEntryJpaEntity> findFirstByLeaseIdOrderByBillingPeriodStartDesc(UUID leaseId);

    /**
     * Backs {@code RentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual}
     * — tenant-agnostic cross-tenant sweep used by {@code RentOverdueScheduler}.
     */
    List<RentLedgerEntryJpaEntity> findAllByStatusInAndDueDateLessThanEqual(
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    /**
     * Backs {@code RentLedgerEntryRepository.findAllByUpdatedAtAfter} —
     * tenant-agnostic sweep used by {@code RentLedgerReconciliationScheduler}.
     */
    List<RentLedgerEntryJpaEntity> findAllByStatusInAndDueDateBetween(
            List<RentLedgerStatus> statuses,
            LocalDate fromInclusive,
            LocalDate toInclusive
    );

    List<RentLedgerEntryJpaEntity> findAllByUpdatedAtAfter(Instant threshold);

    /**
     * Money collected in a billing window, for one landlord.
     *
     * <p>Aggregated in the database rather than by loading entries and summing
     * in Java. The dashboard previously fired five unpaginated list queries to
     * build its numbers, and a landlord with a few hundred units would have
     * pulled their whole portfolio over the wire to render four figures.
     *
     * <p>COALESCE because SUM over no rows is NULL, and a landlord with no
     * entries this month has collected zero, not unknown.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amountPaid), 0)
            FROM RentLedgerEntryJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.billingPeriodStart >= :from
              AND e.billingPeriodStart < :to
            """)
    BigDecimal sumCollectedBetween(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Everything still owed, of any age — {@code amount_due - amount_paid}
     * across unsettled entries.
     *
     * <p>Deliberately not filtered by date: an arrear from four months ago is
     * still owed, and a "current month" outstanding figure would quietly
     * understate what a landlord is chasing.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amountDue - e.amountPaid), 0)
            FROM RentLedgerEntryJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.status IN :statuses
              AND e.amountDue > e.amountPaid
            """)
    BigDecimal sumOutstanding(
            @Param("tenantId") UUID tenantId,
            @Param("statuses") List<RentLedgerStatus> statuses);

    /**
     * The overdue slice of the outstanding total.
     *
     * <p>Two single-value queries rather than one multi-select returning
     * {@code Object[]}: the array form compiles happily and then fails at
     * runtime on the cast if the projection shape ever drifts, which is
     * exactly the kind of defect that reaches production unnoticed.
     */
    @Query("""
            SELECT COALESCE(SUM(e.amountDue - e.amountPaid), 0)
            FROM RentLedgerEntryJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.status = :status
              AND e.amountDue > e.amountPaid
            """)
    BigDecimal sumByStatus(
            @Param("tenantId") UUID tenantId,
            @Param("status") RentLedgerStatus status);

    @Query("""
            SELECT COUNT(e)
            FROM RentLedgerEntryJpaEntity e
            WHERE e.tenantId = :tenantId
              AND e.status = :status
              AND e.amountDue > e.amountPaid
            """)
    long countByStatusUnsettled(
            @Param("tenantId") UUID tenantId,
            @Param("status") RentLedgerStatus status);
}