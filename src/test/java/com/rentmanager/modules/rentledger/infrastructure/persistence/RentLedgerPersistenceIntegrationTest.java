package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres verification of the rent-ledger persistence layer.
 * Domain-level correctness is already covered by RentLedgerEntryTest/
 * RentTransactionTest (pure unit tests, no DB) and RentLedgerApplicationServiceTest
 * (Mockito, no DB) — this suite exists specifically to catch what those
 * two layers structurally cannot: DB constraint enforcement, tenant-scoped
 * query correctness, and optimistic locking actually incrementing
 * `version` on a real UPDATE.
 *
 * PITFALL #1 (read before writing further persistence integration tests
 * in this codebase): the two constraint-violation tests originally
 * asserted DataIntegrityViolationException but got a raw
 * org.hibernate.exception.ConstraintViolationException instead.
 * Root cause: Spring's exception-translation advice
 * (PersistenceExceptionTranslationInterceptor) only wraps calls that go
 * THROUGH a Spring-managed repository/bean proxy; both failing tests
 * threw the violation inside a raw, directly-injected
 * EntityManager.flush() call, so no proxy advice was on the call stack.
 * Fix: flushAndTranslate() below wraps entityManager.flush() and
 * explicitly re-translates any caught RuntimeException using
 * HibernateJpaDialect.translateExceptionIfPossible(...) -- NOT
 * EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(...),
 * which was tried first and is insufficient: it only recognizes
 * jakarta.persistence.PersistenceException subtypes, not Hibernate-native
 * exceptions like ConstraintViolationException, and silently wraps them
 * as JpaSystemException instead of DataIntegrityViolationException.
 *
 * PITFALL #2 (a real Postgres transaction-semantics issue, not a Spring
 * translation issue): once flushAndTranslate() correctly surfaces the
 * constraint violation, Postgres has already marked the ENTIRE current
 * transaction as aborted (SQLState 25P02) -- every subsequent statement
 * on that connection fails with "current transaction is aborted" until a
 * ROLLBACK happens. enforcesPartialUniqueExternalReferenceOnlyWhenPresent
 * originally kept using the same @Transactional test transaction AFTER
 * triggering its violation (to then verify NULL external references
 * don't collide), which hit this.
 *
 * A PROPAGATION_NESTED / real-savepoint fix was attempted here first and
 * abandoned: Spring's JpaTransactionManager savepoint support depends on
 * JpaDialect.getJdbcConnection(...) reaching a genuinely usable JDBC
 * connection, and in this stack (Hibernate 6 + Spring Boot 3.2.5 +
 * HikariCP via Testcontainers Postgres) it consistently failed with
 * NestedTransactionNotSupportedException even with HibernateJpaDialect
 * explicitly set -- this savepoint path is fragile enough across
 * JPA-provider/pool/Spring-version combinations that it isn't worth
 * relying on here.
 *
 * Actual fix: reorder enforcesPartialUniqueExternalReferenceOnlyWhenPresent
 * so the constraint-violation assertion is the LAST action in the test
 * method, exactly like enforcesUniqueLeasePeriodConstraint (which never
 * hit this problem, because it also ends immediately after its
 * violation). This sidesteps the poisoned-transaction issue entirely --
 * nothing runs afterward that could hit SQLState 25P02 -- with no
 * savepoint machinery needed. This mirrors a real request anyway: a real
 * transaction ends (commit or rollback) at the violation; it doesn't
 * chain unrelated business logic onto an already-poisoned transaction.
 */
class RentLedgerPersistenceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RentLedgerEntryRepository rentLedgerEntryRepository;
    @Autowired
    private RentTransactionRepository rentTransactionRepository;
    @Autowired
    private EntityManager entityManager;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    private RentLedgerEntry newEntry(LocalDate periodStart) {
        return RentLedgerEntry.create(
                tenantId, "corr", leaseId, unitId, tenantProfileId,
                periodStart, periodStart.plusDays(29), periodStart,
                new BigDecimal("1000.00"), false
        );
    }

    /**
     * Flushes the persistence context and, if a constraint violation surfaces,
     * re-throws it translated into Spring's DataAccessException hierarchy.
     * See PITFALL #1 above for why HibernateJpaDialect specifically, not
     * EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible(...).
     */
    private void flushAndTranslate() {
        try {
            entityManager.flush();
        } catch (RuntimeException ex) {
            DataAccessException translated = new HibernateJpaDialect().translateExceptionIfPossible(ex);
            throw (translated != null) ? translated : ex;
        }
    }

    @Test
    @Transactional
    void savesAndReloadsEntryWithVersionAndTimestampsPopulated() {
        RentLedgerEntry entry = newEntry(LocalDate.of(2026, 6, 1));

        RentLedgerEntry saved = rentLedgerEntryRepository.save(entry);
        entityManager.flush();
        entityManager.clear();

        Optional<RentLedgerEntry> reloaded = rentLedgerEntryRepository.findByIdAndTenantId(saved.getId(), tenantId);

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getVersion()).isEqualTo(0L);
        assertThat(reloaded.get().getCreatedAt()).isNotNull();
        assertThat(reloaded.get().getUpdatedAt()).isNotNull();
        assertThat(reloaded.get().getAmountDue()).isEqualByComparingTo("1000.00");
    }

    @Test
    @Transactional
    void enforcesUniqueLeasePeriodConstraint() {
        LocalDate period = LocalDate.of(2026, 6, 1);
        rentLedgerEntryRepository.save(newEntry(period));
        entityManager.flush();

        RentLedgerEntry duplicate = newEntry(period); // same leaseId + billingPeriodStart

        assertThatThrownBy(() -> {
            rentLedgerEntryRepository.save(duplicate);
            flushAndTranslate();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void findByLeaseIdAndBillingPeriodStartRespectsPeriodBoundary() {
        LocalDate period = LocalDate.of(2026, 6, 1);
        rentLedgerEntryRepository.save(newEntry(period));
        entityManager.flush();

        assertThat(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, period)).isPresent();
        assertThat(rentLedgerEntryRepository.findByLeaseIdAndBillingPeriodStart(leaseId, period.plusMonths(1)))
                .isEmpty();
    }

    @Test
    @Transactional
    void tenantScopedFindDoesNotLeakAcrossTenants() {
        RentLedgerEntry entry = newEntry(LocalDate.of(2026, 6, 1));
        RentLedgerEntry saved = rentLedgerEntryRepository.save(entry);
        entityManager.flush();

        UUID otherTenantId = UUID.randomUUID();
        assertThat(rentLedgerEntryRepository.findByIdAndTenantId(saved.getId(), otherTenantId)).isEmpty();
        assertThat(rentLedgerEntryRepository.findByIdAndTenantId(saved.getId(), tenantId)).isPresent();
    }

    @Test
    @Transactional
    void optimisticLockingIncrementsVersionOnUpdateAndRejectsStaleWrite() {
        RentLedgerEntry saved = rentLedgerEntryRepository.save(newEntry(LocalDate.of(2026, 6, 1)));
        entityManager.flush();
        assertThat(saved.getVersion()).isEqualTo(0L);

        // Simulate two concurrent loads of the same row.
        RentLedgerEntry copyA = rentLedgerEntryRepository.findByIdAndTenantId(saved.getId(), tenantId).orElseThrow();
        entityManager.detach(entityManager.find(
                com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity.class,
                saved.getId()
        ));
        RentLedgerEntry copyB = rentLedgerEntryRepository.findByIdAndTenantId(saved.getId(), tenantId).orElseThrow();

        RentTransaction payment = RentTransaction.create(
                tenantId, copyA.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("100.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
        );
        copyA.applyTransaction("corr", payment);
        rentLedgerEntryRepository.save(copyA);
        entityManager.flush();

        // copyB still holds version 0 — saving it now should fail: the row has moved to version 1.
        RentTransaction otherPayment = RentTransaction.create(
                tenantId, copyB.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("50.00"), null, RentTransactionSource.CASH, "admin-2", LocalDateTime.now()
        );
        copyB.applyTransaction("corr", otherPayment);

        assertThatThrownBy(() -> {
            rentLedgerEntryRepository.save(copyB);
            entityManager.flush();
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @Transactional
    void enforcesPartialUniqueExternalReferenceOnlyWhenPresent() {
        RentLedgerEntry entry = rentLedgerEntryRepository.save(newEntry(LocalDate.of(2026, 6, 1)));
        entityManager.flush();

        // Two NULL external references must NOT collide — partial index only applies WHERE NOT NULL.
        // Deliberately done BEFORE the constraint-violation assertion below: see PITFALL #2 in the
        // class javadoc for why the violation must be the last thing this test does.
        RentTransaction cashA = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("10.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
        );
        RentTransaction cashB = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("20.00"), null, RentTransactionSource.CASH, "admin-1", LocalDateTime.now()
        );
        rentTransactionRepository.save(cashA);
        entityManager.flush();
        rentTransactionRepository.save(cashB); // must NOT throw
        entityManager.flush();

        RentTransaction first = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("400.00"), "MPESA-DUPLICATE-TEST", RentTransactionSource.MPESA,
                "system", LocalDateTime.now()
        );
        rentTransactionRepository.save(first);
        entityManager.flush();

        RentTransaction duplicate = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("400.00"), "MPESA-DUPLICATE-TEST", RentTransactionSource.MPESA,
                "system", LocalDateTime.now()
        );

        // Must be the LAST action in this test method: triggering this violation
        // poisons the entire Postgres transaction (SQLState 25P02 on any further
        // statement), so nothing further can safely run afterward. See PITFALL #2.
        assertThatThrownBy(() -> {
            rentTransactionRepository.save(duplicate);
            flushAndTranslate();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void findByExternalReferenceIsTenantScoped() {
        RentLedgerEntry entry = rentLedgerEntryRepository.save(newEntry(LocalDate.of(2026, 6, 1)));
        entityManager.flush();

        RentTransaction tx = RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT,
                new BigDecimal("400.00"), "MPESA-SCOPE-TEST", RentTransactionSource.MPESA,
                "system", LocalDateTime.now()
        );
        rentTransactionRepository.save(tx);
        entityManager.flush();

        assertThat(rentTransactionRepository.findByExternalReference(tenantId, "MPESA-SCOPE-TEST")).isPresent();
        assertThat(rentTransactionRepository.findByExternalReference(UUID.randomUUID(), "MPESA-SCOPE-TEST"))
                .isEmpty();
    }
}