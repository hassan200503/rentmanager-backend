package com.rentmanager.modules.rentledger.infrastructure.persistence;

import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.support.AbstractPostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
     * Inserts the tenants row this whole test class's shared tenantId needs
     * — rent_ledger_entries.tenant_id (and everything under it) now carries
     * a real foreign key (V72-V75), so every placeholder chain below must
     * resolve to it.
     */
    @BeforeEach
    void createTenant() {
        entityManager.createNativeQuery("""
                INSERT INTO tenants (id, tenant_code, name)
                VALUES (?1, ?2, 'Test Landlord')
                """)
                .setParameter(1, tenantId)
                .setParameter(2, "TEN-" + tenantId)
                .executeUpdate();
        entityManager.flush();
    }

    /**
     * Creates a minimal RentPaymentRequest in PENDING state with a random
     * rentLedgerEntryId that satisfies the FK constraint by first inserting
     * a placeholder property/unit/tenant_profile/lease/rent_ledger_entry
     * chain via JDBC (bypassing the domain model's full constructors to
     * keep these tests focused on the request table's own constraints).
     */
    private UUID createMinimalLedgerEntry() {
        UUID propertyId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO properties (id, tenant_id, reference_code, name, status, created_at, premises_type)
                VALUES (?1, ?2, ?3, 'Test Property', 'ACTIVE', NOW(), 'RESIDENTIAL')
                """)
                .setParameter(1, propertyId)
                .setParameter(2, tenantId)
                .setParameter(3, "PROP-" + propertyId)
                .executeUpdate();

        UUID unitId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO units (id, unit_number, tenant_id, property_id, status, occupancy_status)
                VALUES (?1, ?2, ?3, ?4, 'ACTIVE', 'VACANT')
                """)
                .setParameter(1, unitId)
                .setParameter(2, "U-" + unitId)
                .setParameter(3, tenantId)
                .setParameter(4, propertyId)
                .executeUpdate();

        UUID tenantProfileId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO tenant_profile (id, tenant_id, clerk_user_id, full_name, email, phone)
                VALUES (?1, ?2, ?3, 'Test Renter', 'renter@test.local', '+254711111111')
                """)
                .setParameter(1, tenantProfileId)
                .setParameter(2, tenantId)
                .setParameter(3, "clerk-" + tenantProfileId)
                .executeUpdate();

        UUID leaseId = UUID.randomUUID();
        entityManager.createNativeQuery("""
                INSERT INTO leases
                    (id, tenant_id, property_id, unit_id, tenant_profile_id, lease_number,
                     lease_type, billing_cycle, status, start_date, end_date, rent_amount, deposit_amount)
                VALUES
                    (?1, ?2, ?3, ?4, ?5, ?6, 'FIXED_TERM', 'MONTHLY', 'ACTIVE',
                     '2026-01-01', '2026-12-31', 1000.00, 1000.00)
                """)
                .setParameter(1, leaseId)
                .setParameter(2, tenantId)
                .setParameter(3, propertyId)
                .setParameter(4, unitId)
                .setParameter(5, tenantProfileId)
                .setParameter(6, "LSE-" + leaseId)
                .executeUpdate();

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
                .setParameter(3, leaseId)
                .setParameter(4, unitId)
                .setParameter(5, tenantProfileId)
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
