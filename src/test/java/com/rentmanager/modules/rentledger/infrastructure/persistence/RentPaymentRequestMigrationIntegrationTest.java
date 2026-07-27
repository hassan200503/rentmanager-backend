package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for V39__create_rent_payment_requests.sql's constraints.
 *
 * The partial unique index
 * {@code uk_rent_payment_requests_checkout_request_id} is defined as:
 *   CREATE UNIQUE INDEX ON rent_payment_requests (mpesa_checkout_request_id)
 *   WHERE mpesa_checkout_request_id IS NOT NULL;
 *
 * This test proves:
 *   1. Multiple rows with NULL mpesa_checkout_request_id do not collide.
 *   2. A duplicate non-null value is rejected.
 *   3. The FK to rent_ledger_entries is enforced.
 */
class RentPaymentRequestMigrationIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private RentPaymentRequestRepository repository;

    @Autowired
    private EntityManager entityManager;

    private final UUID tenantId = UUID.randomUUID();
    private static final String CUSTOMER_PHONE = "254712345678";
    private static final String CHECKOUT_ID = "ws_CO_" + UUID.randomUUID();

    /**
     * Creates a minimal RentPaymentRequest in PENDING state with a random
     * rentLedgerEntryId that satisfies the FK constraint by first inserting
     * a placeholder rent_ledger_entry row via JDBC (bypassing the domain
     * model's full constructor to keep these tests focused on the request
     * table's own constraints).
     */
    private UUID createMinimalLedgerEntry() {
        UUID id = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO rent_ledger_entries
                    (id, tenant_id, lease_id, unit_id, tenant_profile_id,
                     billing_period_start, billing_period_end, due_date,
                     amount_due, amount_paid, status, version, created_at, updated_at)
                VALUES
                    (?1, ?2, ?3, ?4, ?5,
                     '2026-01-01', '2026-01-31', '2026-01-01',
                     1000.00, 0.00, 'PENDING', 0, NOW(), NOW())
                """)
                .setParameter(1, id)
                .setParameter(2, tenantId)
                .setParameter(3, UUID.randomUUID())
                .setParameter(4, UUID.randomUUID())
                .setParameter(5, UUID.randomUUID())
                .executeUpdate();
        entityManager.flush();
        return id;
    }

    private RentPaymentRequest createRequest() {
        UUID entryId = createMinimalLedgerEntry();
        return RentPaymentRequest.create(
                tenantId, UUID.randomUUID(), entryId, new BigDecimal("1000.00")
        );
    }

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
    void multipleNullCheckoutRequestIds_allowed() {
        RentPaymentRequest r1 = createRequest();
        RentPaymentRequest r2 = createRequest();
        RentPaymentRequest r3 = createRequest();

        repository.save(r1);
        entityManager.flush();
        repository.save(r2);
        entityManager.flush();
        repository.save(r3);
        entityManager.flush();

        assertThat(r1.getMpesaCheckoutRequestId()).isNull();
        assertThat(r2.getMpesaCheckoutRequestId()).isNull();
        assertThat(r3.getMpesaCheckoutRequestId()).isNull();
    }

    @Test
    @Transactional
    void duplicateCheckoutRequestId_rejected() {
        RentPaymentRequest first = createRequest();
        first.attachCheckoutRequestId(CHECKOUT_ID);
        repository.save(first);
        entityManager.flush();

        RentPaymentRequest second = createRequest();
        second.attachCheckoutRequestId(CHECKOUT_ID);

        assertThatThrownBy(() -> {
            repository.save(second);
            flushAndTranslate();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void nonexistentLedgerEntryId_rejected() {
        UUID fakeEntryId = UUID.randomUUID();

        assertThatThrownBy(() -> {
            try {
                entityManager.createNativeQuery("""
                        INSERT INTO rent_payment_requests
                            (id, tenant_id, lease_id, rent_ledger_entry_id,
                             amount, status, version, created_at, updated_at)
                        VALUES
                            (?1, ?2, ?3, ?4,
                             1000.00, 'PENDING', 0, NOW(), NOW())
                        """)
                        .setParameter(1, UUID.randomUUID())
                        .setParameter(2, tenantId)
                        .setParameter(3, UUID.randomUUID())
                        .setParameter(4, fakeEntryId)
                        .executeUpdate();
                entityManager.flush();
            } catch (RuntimeException ex) {
                DataAccessException translated = new HibernateJpaDialect().translateExceptionIfPossible(ex);
                throw (translated != null) ? translated : ex;
            }
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void findByMpesaCheckoutRequestId_roundTrips() {
        UUID entryId = createMinimalLedgerEntry();
        RentPaymentRequest request = RentPaymentRequest.create(
                tenantId, UUID.randomUUID(), entryId, new BigDecimal("500.00")
        );
        request.attachCheckoutRequestId(CHECKOUT_ID + "-roundtrip");
        repository.save(request);
        entityManager.flush();
        entityManager.clear();

        var found = repository.findByMpesaCheckoutRequestId(CHECKOUT_ID + "-roundtrip");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(request.getId());
        assertThat(found.get().getAmount()).isEqualByComparingTo("500.00");
        assertThat(found.get().getStatus()).isEqualTo(RentPaymentRequestStatus.PENDING);
    }
}
